public class SkipListNode {
    String playerId;
    int score;
    long timestamp;
    SkipListNode[] forward;
    int[] span; // Tracks the number of steps traversed at each level

    public SkipListNode(String playerId, int score, long timestamp, int level) {
        this.playerId = playerId;
        this.score = score;
        this.timestamp = timestamp;
        this.forward = new SkipListNode[level];
        this.span = new int[level];
    }
}