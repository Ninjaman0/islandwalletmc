package com.ninja.islandwallet.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CRITICAL: Represents island wallet data with COMPLETELY SEPARATED Gems and payout points
 *
 * GEMS: Island's shared gem balance (NOT linked to payout system)
 * PAYOUT POINTS: Points for leaderboard ranking ONLY (purchased with gems)
 */
public class IslandData {

    private final String islandId;
    private volatile String islandName;
    private volatile String leader;
    private volatile String admin;
    private final List<String> members;

    // CRITICAL SEPARATION: These are completely independent systems
    private final AtomicLong gems; // Shared island gems (separate from payout)
    private final AtomicLong payoutPoints; // Points for payout leaderboard ONLY

    private volatile UUID leaderUUID;

    // Thread-safety for member operations
    private final ReentrantReadWriteLock membersLock = new ReentrantReadWriteLock();

    public IslandData(String islandId, String islandName) {
        if (islandId == null || islandId.trim().isEmpty()) {
            throw new IllegalArgumentException("Island ID cannot be null or empty");
        }
        if (islandName == null || islandName.trim().isEmpty()) {
            throw new IllegalArgumentException("Island name cannot be null or empty");
        }

        this.islandId = islandId.trim();
        this.islandName = islandName.trim();
        this.members = new ArrayList<>();
        this.gems = new AtomicLong(0);
        this.payoutPoints = new AtomicLong(0);
    }

    // Getters with proper validation
    public String getIslandId() {
        return islandId;
    }

    public String getIslandName() {
        return islandName != null ? islandName : "Unknown";
    }

    public String getLeader() {
        return leader;
    }

    public String getAdmin() {
        return admin;
    }

    public List<String> getMembers() {
        membersLock.readLock().lock();
        try {
            return new ArrayList<>(members);
        } finally {
            membersLock.readLock().unlock();
        }
    }

    /**
     * CRITICAL: Get island's gem balance (NOT connected to payout system)
     */
    public long getGems() {
        return gems.get();
    }

    /**
     * CRITICAL: Get payout points (ONLY for leaderboard ranking)
     */
    public long getPayoutPoints() {
        return payoutPoints.get();
    }

    public UUID getLeaderUUID() {
        return leaderUUID;
    }

    // Setters with validation
    public void setIslandName(String islandName) {
        if (islandName == null || islandName.trim().isEmpty()) {
            throw new IllegalArgumentException("Island name cannot be null or empty");
        }
        this.islandName = islandName.trim();
    }

    public void setLeader(String leader) {
        this.leader = leader != null ? leader.trim() : null;
    }

    public void setAdmin(String admin) {
        this.admin = admin != null ? admin.trim() : null;
    }

    public void setMembers(List<String> members) {
        if (members == null) {
            members = new ArrayList<>();
        }

        membersLock.writeLock().lock();
        try {
            this.members.clear();
            for (String member : members) {
                if (member != null && !member.trim().isEmpty()) {
                    this.members.add(member.trim());
                }
            }
        } finally {
            membersLock.writeLock().unlock();
        }
    }

    /**
     * CRITICAL: Set gem balance (separate from payout system)
     */
    public void setGems(long gems) {
        if (gems < 0) {
            throw new IllegalArgumentException("Gems cannot be negative");
        }
        this.gems.set(gems);
    }

    /**
     * CRITICAL: Set payout points (only for leaderboard)
     */
    public void setPayoutPoints(long payoutPoints) {
        if (payoutPoints < 0) {
            throw new IllegalArgumentException("Payout points cannot be negative");
        }
        this.payoutPoints.set(payoutPoints);
    }

    public void setLeaderUUID(UUID leaderUUID) {
        this.leaderUUID = leaderUUID;
    }

    // Thread-safe member operations
    public void addMember(String member) {
        if (member == null || member.trim().isEmpty()) {
            return;
        }

        String cleanMember = member.trim();
        membersLock.writeLock().lock();
        try {
            if (!members.contains(cleanMember)) {
                members.add(cleanMember);
            }
        } finally {
            membersLock.writeLock().unlock();
        }
    }

    public void removeMember(String member) {
        if (member == null) {
            return;
        }

        membersLock.writeLock().lock();
        try {
            members.remove(member.trim());
        } finally {
            membersLock.writeLock().unlock();
        }
    }

    public boolean isMember(String playerName) {
        if (playerName == null) {
            return false;
        }

        String cleanPlayerName = playerName.trim();

        // Check leader
        if (leader != null && leader.equals(cleanPlayerName)) {
            return true;
        }

        // Check admin
        if (admin != null && admin.equals(cleanPlayerName)) {
            return true;
        }

        // Check members list
        membersLock.readLock().lock();
        try {
            return members.contains(cleanPlayerName);
        } finally {
            membersLock.readLock().unlock();
        }
    }

    public int getMemberCount() {
        membersLock.readLock().lock();
        try {
            int count = members.size();
            if (leader != null) count++;
            if (admin != null) count++;
            return count;
        } finally {
            membersLock.readLock().unlock();
        }
    }

    // CRITICAL: Thread-safe gem operations (completely separate from payout)
    public long addGems(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot add negative gems");
        }

        return gems.addAndGet(amount);
    }

    public boolean withdrawGems(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot withdraw negative gems");
        }

        while (true) {
            long currentGems = gems.get();
            if (currentGems < amount) {
                return false; // Insufficient gems
            }
            if (gems.compareAndSet(currentGems, currentGems - amount)) {
                return true; // Successfully withdrew
            }
            // Retry if another thread modified the value
        }
    }

    // CRITICAL: Thread-safe payout point operations (separate from gems)
    public long addPayoutPoints(long points) {
        if (points < 0) {
            throw new IllegalArgumentException("Cannot add negative payout points");
        }

        return payoutPoints.addAndGet(points);
    }

    public void resetPayoutPoints() {
        payoutPoints.set(0);
    }

    // Legacy compatibility methods (now maps to gems for backwards compatibility)
    @Deprecated
    public long getPlayerPoints() {
        return getGems();
    }

    @Deprecated
    public void setPlayerPoints(long points) {
        setGems(points);
    }

    @Deprecated
    public void addPlayerPoints(long points) {
        addGems(points);
    }

    // Validation method
    public boolean isValid() {
        return islandId != null && !islandId.trim().isEmpty() &&
                islandName != null && !islandName.trim().isEmpty() &&
                gems.get() >= 0 && payoutPoints.get() >= 0;
    }

    @Override
    public String toString() {
        return "IslandData{" +
                "islandId='" + islandId + '\'' +
                ", islandName='" + islandName + '\'' +
                ", leader='" + leader + '\'' +
                ", admin='" + admin + '\'' +
                ", memberCount=" + getMemberCount() +
                ", gems=" + gems.get() +
                ", payoutPoints=" + payoutPoints.get() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IslandData that = (IslandData) o;
        return islandId.equals(that.islandId);
    }

    @Override
    public int hashCode() {
        return islandId.hashCode();
    }
}