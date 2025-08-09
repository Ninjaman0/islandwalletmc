package com.ninja.islandwallet.models;

import java.time.LocalDateTime;

/**
 * Represents a payout winner for historical data
 */
public class PayoutWinner {
    
    private final String islandId;
    private final String islandName;
    private final String leader;
    private final long points;
    private final int rank;
    private final LocalDateTime payoutDate;
    private final int season;
    
    public PayoutWinner(String islandId, String islandName, String leader, long points, int rank, LocalDateTime payoutDate, int season) {
        this.islandId = islandId;
        this.islandName = islandName;
        this.leader = leader;
        this.points = points;
        this.rank = rank;
        this.payoutDate = payoutDate;
        this.season = season;
    }
    
    // Getters
    public String getIslandId() {
        return islandId;
    }
    
    public String getIslandName() {
        return islandName;
    }
    
    public String getLeader() {
        return leader;
    }
    
    public long getPoints() {
        return points;
    }
    
    public int getRank() {
        return rank;
    }
    
    public LocalDateTime getPayoutDate() {
        return payoutDate;
    }
    
    public int getSeason() {
        return season;
    }
    
    @Override
    public String toString() {
        return "PayoutWinner{" +
                "islandName='" + islandName + '\'' +
                ", leader='" + leader + '\'' +
                ", points=" + points +
                ", rank=" + rank +
                ", season=" + season +
                '}';
    }
}