import requests
import streamlit as st

# API_URL = "http://localhost:8080/leaderboard"
API_URL = "https://redis-project-ec14.onrender.com/"

st.set_page_config(page_title="eSports Tournament Engine", page_icon="🎮", layout="centered")
st.title("🎮 Multi-Tiered Skip List Matchmaking Engine")
st.caption("Features O(log n) Span-Based Ranks, Tier Migration, & Percentile Analytics")

# Sidebar for submitting scores & triggering automatic tier migration
st.sidebar.header("🕹️ Match Result Submission")
player_id = st.sidebar.text_input("Player Tag", "Valkyrie")
score = st.sidebar.number_input("Match Score", min_value=0, max_value=5000, value=3200, step=100)
st.sidebar.caption("Thresholds: Bronze (<1500) | Silver (1500-2999) | Gold (3000+)")

if st.sidebar.button("Submit Score (ZADD)", use_container_width=True):
    try:
        resp = requests.post(f"{API_URL}?id={player_id}&score={score}")
        if resp.status_code == 200:
            st.sidebar.success(f"Synced {player_id}! Checked tier placement.")
        else:
            st.sidebar.error("Submission failed.")
    except Exception:
        st.sidebar.error("Backend offline.")

st.sidebar.divider()

# Sidebar for querying player profile & exact percentile
st.sidebar.header("🔍 Player Analytics (ZRANK)")
search_id = st.sidebar.text_input("Search Player ID", "ApexPredator")
if st.sidebar.button("Fetch Profile & Percentile", use_container_width=True):
    try:
        data = requests.get(f"{API_URL}?id={search_id}").json()
        if "error" not in data:
            st.sidebar.success(f"**{data['playerId']}**")
            st.sidebar.info(f"🏆 Tier: **{data['tier']}**\n\n⭐ Score: **{data['score']}**\n\n📌 Rank: **#{data['rank']}**\n\n📈 Top **{data['percentile']}%** of players")
        else:
            st.sidebar.warning("Player not found.")
    except Exception:
        st.sidebar.error("Could not reach backend.")

# Main Dashboard View across League Tiers
st.subheader("📊 Live Tier Leaderboards")

if st.button("🔄 Refresh Dashboards", use_container_width=True):
    st.rerun()

try:
    res = requests.get(API_URL)
    if res.status_code == 200:
        data = res.json()
        
        # Create tabs for each competitive tier
        tab_gold, tab_silver, tab_bronze = st.tabs(["🟡 Gold Tier", "⚪ Silver Tier", "🥉 Bronze Tier"])
        
        tiers_mapping = [("Gold", tab_gold), ("Silver", tab_silver), ("Bronze", tab_bronze)]
        
        for t_name, t_tab in tiers_mapping:
            with t_tab:
                player_list = data.get(t_name, [])
                if len(player_list) > 0:
                    for entry in player_list:
                        rank = entry.get("rank")
                        pid = entry.get("playerId")
                        p_score = entry.get("score")
                        
                        medal = "🥇" if rank == 1 else ("🥈" if rank == 2 else ("🥉" if rank == 3 else "🔸"))
                        
                        c1, c2, c3 = st.columns([1, 4, 2])
                        c1.markdown(f"### {medal} #{rank}")
                        c2.markdown(f"### `{pid}`")
                        c3.metric("Score", p_score)
                        st.divider()
                else:
                    st.info(f"No players active in {t_name} tier.")
    else:
        st.warning("Failed to load tier structures.")
except Exception:
    st.warning("⚠️ Start your Java backend server to view data.")