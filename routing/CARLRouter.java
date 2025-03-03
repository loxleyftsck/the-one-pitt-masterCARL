package routing;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

public class CARLRouter extends ActiveRouter {

    // Parameter Reinforcement Learning
    private static final double ALPHA = 0.1; // Learning rate
    private static final double GAMMA = 0.9; // Discount factor
    private static final double INITIAL_EPSILON = 0.3; // Exploration rate awal
    private double epsilon = INITIAL_EPSILON; // Adaptive exploration rate

    // Q-Table untuk nilai pembelajaran
    private final HashMap<String, Double> qTable = new HashMap<>();

    // Variabel status node
    private final HashMap<Integer, Double> batteryLevels = new HashMap<>();
    private final HashMap<Integer, Double> tieStrengths = new HashMap<>();
    private final HashMap<Integer, Double> popularity = new HashMap<>();

    private final Random rand = new Random();
    private final Settings settings;
    private boolean isInitialized = false;

    public CARLRouter(Settings settings) {
        super(settings);
        this.settings = settings;
    }

    @Override
    public void update() {
        if (!isInitialized) {
            initializeNodeProperties();
            isInitialized = true;
        }

        super.update();
        List<Message> messages = new ArrayList<>(getMessageCollection());

        for (Message msg : messages) {
            DTNHost bestNextHop = selectNextHop(msg);

            if (bestNextHop != null) {
                boolean messageSent = false;
                for (Connection conn : this.getHost().getConnections()) {
                    if (conn.getOtherNode(this.getHost()).equals(bestNextHop)) {
                        sendMessage(msg.getId(), bestNextHop);
                        // ✅ Perbaikan di sini
                        updateQTable(msg.getId(), bestNextHop, calculateReward(bestNextHop));
                        updateTieStrength(bestNextHop);
                        updateBatteryLevel(bestNextHop);
                        messageSent = true;
                        break;
                    }
                }
                if (!messageSent) {
                    updateQTable(msg.getId(), null, -0.5);
                }
            } else {
                updateQTable(msg.getId(), null, -1.0);
            }
        }
    }

    /**
     * Memilih next-hop terbaik berdasarkan fuzzy logic
     */
    private DTNHost selectNextHop(Message msg) {
        List<Connection> connections = new ArrayList<>(this.getHost().getConnections());
        if (connections.isEmpty()) {
            return null;
        }

        DTNHost bestHost = null;
        double maxScore = Double.NEGATIVE_INFINITY;

        if (rand.nextDouble() < epsilon) {
            bestHost = connections.get(rand.nextInt(connections.size())).getOtherNode(getHost());
        } else {
            for (Connection conn : connections) {
                DTNHost neighbor = conn.getOtherNode(getHost());
                double score = fuzzyEvaluation(neighbor);

                if (score > maxScore) {
                    maxScore = score;
                    bestHost = neighbor;
                }
            }
        }
        return bestHost;
    }

    /**
     * Evaluasi fuzzy logic berdasarkan beberapa parameter
     */
    private double fuzzyEvaluation(DTNHost neighbor) {
        double battery = getBatteryLevel(neighbor);
        double buffer = getBufferOccupancy(neighbor);
        double tieStrength = getTieStrength(neighbor);
        double popularity = getPopularity(neighbor);

        double batteryFuzzy = fuzzifyBattery(battery);
        double bufferFuzzy = fuzzifyBuffer(buffer);
        double tieFuzzy = fuzzifyTie(tieStrength);
        double popularityFuzzy = fuzzifyPopularity(popularity);

        return (0.4 * batteryFuzzy) + (0.3 * tieFuzzy) + (0.2 * popularityFuzzy) - (0.1 * bufferFuzzy);
    }

    /**
     * Menghitung reward untuk update Q-table
     */
    private double calculateReward(DTNHost nextHop) {
        double battery = getBatteryLevel(nextHop);
        double tieStrength = getTieStrength(nextHop);
        double buffer = getBufferOccupancy(nextHop);
        double popularity = getPopularity(nextHop);

        return (0.5 * tieStrength) + (0.3 * battery) + (0.1 * popularity) - (0.1 * buffer);
    }

    /**
     * Fungsi fuzzifikasi nilai
     */
    private double fuzzifyBattery(double value) {
        if (value >= 80) return 1.0;
        else if (value >= 40) return 0.5;
        else return 0.1;
    }

    private double fuzzifyBuffer(double value) {
        if (value >= 80) return 1.0;
        else if (value >= 40) return 0.5;
        else return 0.1;
    }

    private double fuzzifyTie(double value) {
        if (value >= 0.7) return 1.0;
        else if (value >= 0.4) return 0.5;
        else return 0.1;
    }

    private double fuzzifyPopularity(double value) {
        if (value >= 0.7) return 1.0;
        else if (value >= 0.4) return 0.5;
        else return 0.1;
    }

    /**
     * Update baterai setelah pengiriman pesan
     */
    private void updateBatteryLevel(DTNHost host) {
        double currentBattery = batteryLevels.getOrDefault(host.getAddress(), 1.0);
        batteryLevels.put(host.getAddress(), Math.max(0.0, currentBattery - 0.05));
    }

    /**
     * Update hubungan sosial setelah bertukar pesan
     */
    private void updateTieStrength(DTNHost receiver) {
        double currentStrength = tieStrengths.getOrDefault(receiver.getAddress(), 0.5);
        tieStrengths.put(receiver.getAddress(), Math.min(1.0, currentStrength + 0.1));
    }

    private double getBatteryLevel(DTNHost host) {
        return batteryLevels.getOrDefault(host.getAddress(), 1.0);
    }

    private double getTieStrength(DTNHost host) {
        return tieStrengths.getOrDefault(host.getAddress(), 0.5);
    }

    private double getBufferOccupancy(DTNHost host) {
        int bufferSize = host.getMessageCollection().size();
        int totalBufferSize = Math.max(bufferSize, 100); // Pastikan total buffer lebih dari 0
        return 1.0 - ((double) bufferSize / totalBufferSize);
    }

    private double getPopularity(DTNHost host) {
        return popularity.getOrDefault(host.getAddress(), 0.5);
    }

    /**
     * Update nilai Q-table
     */
    private void updateQTable(String msgId, DTNHost nextHop, double reward) {
        String stateAction = msgId + "->" + (nextHop != null ? nextHop.getAddress() : "null");
        double oldQValue = qTable.getOrDefault(stateAction, 0.0);
        double newQValue = oldQValue + ALPHA * (reward + GAMMA * 0 - oldQValue);
        qTable.put(stateAction, newQValue);
    }

    private void initializeNodeProperties() {
        for (Connection conn : this.getHost().getConnections()) {
            DTNHost neighbor = conn.getOtherNode(this.getHost());
            batteryLevels.put(neighbor.getAddress(), 1.0);
            tieStrengths.put(neighbor.getAddress(), 0.5);
            popularity.put(neighbor.getAddress(), 0.5);
        }
    }

    @Override
    public CARLRouter replicate() {
        return new CARLRouter(this.settings);
    }
}
