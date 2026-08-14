import java.util.*;

public class SkipListLeaderboard {
    private static final int MAX_LEVEL = 16;
    private static final double P = 0.5;
    private final Random random = new Random();

    // Multi-tier support: Bronze (< 1500), Silver (< 3000), Gold (3000+)
    private final Map<String, SkipListTier> tiers;
    private final Map<String, String> playerTierMap; // Maps playerId -> tier name

    public SkipListLeaderboard() {
        this.tiers = new HashMap<>();
        this.tiers.put("Bronze", new SkipListTier("Bronze"));
        this.tiers.put("Silver", new SkipListTier("Silver"));
        this.tiers.put("Gold", new SkipListTier("Gold"));
        this.playerTierMap = new HashMap<>();
    }

    private String determineTier(int score) {
        if (score >= 3000) return "Gold";
        if (score >= 1500) return "Silver";
        return "Bronze";
    }

    public void zadd(String playerId, int score) {
        String newTier = determineTier(score);
        String oldTier = playerTierMap.get(playerId);

        // If player changed tiers, remove them from the old tier's SkipList
        if (oldTier != null && !oldTier.equals(newTier)) {
            tiers.get(oldTier).zrem(playerId);
        }

        playerTierMap.put(playerId, newTier);
        tiers.get(newTier).zadd(playerId, score);
    }

    public void zrem(String playerId) {
        String tier = playerTierMap.remove(playerId);
        if (tier != null) {
            tiers.get(tier).zrem(playerId);
        }
    }

    // Get top N for a specific tier or aggregated
    public List<Map<String, Object>> zrange(String tierName, int start, int end) {
        if (!tiers.containsKey(tierName)) return new ArrayList<>();
        return tiers.get(tierName).zrange(start, end);
    }

    // True O(log n) rank lookup utilizing skip-list spans
    public Map<String, Object> getPlayerInfo(String playerId) {
        String tierName = playerTierMap.get(playerId);
        if (tierName == null) return null;

        SkipListTier tier = tiers.get(tierName);
        int rank = tier.zrank(playerId);
        int score = tier.getPlayerScore(playerId);
        int totalPlayers = tier.size();

        Map<String, Object> info = new HashMap<>();
        info.put("playerId", playerId);
        info.put("tier", tierName);
        info.put("score", score);
        info.put("rank", rank);
        info.put("totalInTier", totalPlayers);
        info.put("percentile", totalPlayers > 1 ? Math.round((1.0 - (double)(rank - 1) / totalPlayers) * 100.0) : 100);
        return info;
    }

    public Map<String, List<Map<String, Object>>> getAllTiersTop(int limit) {
        Map<String, List<Map<String, Object>>> all = new HashMap<>();
        for (String t : tiers.keySet()) {
            all.put(t, tiers.get(t).zrange(0, limit - 1));
        }
        return all;
    }

    // Inner class representing a Skip List for a specific competitive tier
    public static class SkipListTier {
        private final String tierName;
        private final SkipListNode head;
        private int currentMaxLevel;
        private int size;
        private final Map<String, SkipListNode> playerMap;
        private final Random random = new Random();

        public SkipListTier(String tierName) {
            this.tierName = tierName;
            this.head = new SkipListNode("", Integer.MIN_VALUE, 0, MAX_LEVEL);
            this.currentMaxLevel = 1;
            this.size = 0;
            this.playerMap = new HashMap<>();
        }

        private int randomLevel() {
            int lvl = 1;
            while (random.nextDouble() < P && lvl < MAX_LEVEL) lvl++;
            return lvl;
        }

        public int size() { return size; }
        public int getPlayerScore(String pid) {
            return playerMap.containsKey(pid) ? playerMap.get(pid).score : 0;
        }

        public void zadd(String playerId, int score) {
            long timestamp = System.currentTimeMillis();
            if (playerMap.containsKey(playerId)) {
                zrem(playerId);
            }

            int newLevel = randomLevel();
            if (newLevel > currentMaxLevel) currentMaxLevel = newLevel;

            SkipListNode newNode = new SkipListNode(playerId, score, timestamp, newLevel);
            SkipListNode[] update = new SkipListNode[MAX_LEVEL];
            int[] rankCount = new int[MAX_LEVEL];
            SkipListNode curr = head;

            for (int i = currentMaxLevel - 1; i >= 0; i--) {
                rankCount[i] = (i == currentMaxLevel - 1) ? 0 : rankCount[i + 1];
                while (curr.forward[i] != null && 
                      (curr.forward[i].score > score || 
                      (curr.forward[i].score == score && curr.forward[i].timestamp < timestamp))) {
                    rankCount[i] += curr.span[i];
                    curr = curr.forward[i];
                }
                update[i] = curr;
            }

            for (int i = 0; i < newLevel; i++) {
                newNode.forward[i] = update[i].forward[i];
                update[i].forward[i] = newNode;

                // Span adjustment calculations
                newNode.span[i] = update[i].span[i] - (rankCount[0] - rankCount[i]);
                update[i].span[i] = (rankCount[0] - rankCount[i]) + 1;
            }

            for (int i = newLevel; i < currentMaxLevel; i++) {
                update[i].span[i]++;
            }

            playerMap.put(playerId, newNode);
            size++;
        }

        public void zrem(String playerId) {
            if (!playerMap.containsKey(playerId)) return;
            SkipListNode target = playerMap.get(playerId);
            SkipListNode[] update = new SkipListNode[MAX_LEVEL];
            SkipListNode curr = head;

            for (int i = currentMaxLevel - 1; i >= 0; i--) {
                while (curr.forward[i] != null && curr.forward[i] != target) {
                    curr = curr.forward[i];
                }
                update[i] = curr;
            }

            for (int i = 0; i < currentMaxLevel; i++) {
                if (update[i].forward[i] == target) {
                    update[i].span[i] += target.span[i] - 1;
                    update[i].forward[i] = target.forward[i];
                } else {
                    update[i].span[i]--;
                }
            }

            playerMap.remove(playerId);
            size--;

            while (currentMaxLevel > 1 && head.forward[currentMaxLevel - 1] == null) {
                currentMaxLevel--;
            }
        }

        public int zrank(String playerId) {
            if (!playerMap.containsKey(playerId)) return -1;
            SkipListNode target = playerMap.get(playerId);
            int rank = 0;
            SkipListNode curr = head;

            for (int i = currentMaxLevel - 1; i >= 0; i--) {
                while (curr.forward[i] != null && 
                      (curr.forward[i].score > target.score || 
                      (curr.forward[i].score == target.score && curr.forward[i].timestamp <= target.timestamp))) {
                    rank += curr.span[i];
                    curr = curr.forward[i];
                }
                if (curr == target) break;
            }
            return rank;
        }

        public List<Map<String, Object>> zrange(int start, int end) {
            List<Map<String, Object>> result = new ArrayList<>();
            SkipListNode curr = head.forward[0];
            int index = 0;

            while (curr != null) {
                if (index >= start && index <= end) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("rank", index + 1);
                    entry.put("playerId", curr.playerId);
                    entry.put("score", curr.score);
                    result.add(entry);
                }
                if (index > end) break;
                curr = curr.forward[0];
                index++;
            }
            return result;
        }
    }
}