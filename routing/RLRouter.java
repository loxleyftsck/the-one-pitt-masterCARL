package routing;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

public class RLRouter extends ActiveRouter {

    private static final double LEARNING_RATE = 0.1;
    private static final double DISCOUNT_FACTOR = 0.9;
    private static final double EPSILON = 0.2;  // Exploration rate
    private final HashMap<String, Double> qTable;
    private final Random rand;
    private final Settings settings;  // Simpan pengaturan dari konstruktor

    public RLRouter(Settings settings) {
        super(settings);
        this.qTable = new HashMap<>();
        this.rand = new Random();
        this.settings = settings;  // Simpan pengaturan
    }

    @Override
    public void update() {
        super.update();

        // Dapatkan semua pesan yang dimiliki node
        List<Message> messages = new ArrayList<>(getMessageCollection());

        for (Message msg : messages) {
            DTNHost bestNextHop = selectNextHop(msg);

            if (bestNextHop != null) {
                boolean messageSent = false;
                for (Connection conn : this.getHost().getConnections()) {
                    // Cek apakah koneksi menuju bestNextHop
                    if (conn.getOtherNode(this.getHost()).equals(bestNextHop)) {
                        sendMessage(msg.getId(), bestNextHop); // Kirim ID pesan dan node tujuan
                        updateQTable(msg.getId(), bestNextHop, 1.0); // Reward untuk pengiriman sukses
                        messageSent = true;
                        break;
                    }
                }

                // Jika tidak ada koneksi yang valid, log peringatan
                if (!messageSent) {
                    System.out.println("Warning: No valid connection found for message " + msg.getId());
                    updateQTable(msg.getId(), null, -0.5); // Penalti kecil untuk kondisi ini
                }
            } else {
                // Tidak ada node terbaik yang ditemukan, log dan penalti
                System.out.println("No next hop found for message " + msg.getId());
                updateQTable(msg.getId(), null, -1.0); // Penalti besar untuk kegagalan total
            }
        }
    }

    private DTNHost selectNextHop(Message msg) {
        List<Connection> connections = new ArrayList<>(this.getHost().getConnections());
        if (connections.isEmpty()) {
            System.out.println("No connections available for host: " + this.getHost().getAddress());
            return null;
        }

        DTNHost bestHost = null;
        double maxQValue = Double.NEGATIVE_INFINITY;

        if (rand.nextDouble() < EPSILON) {
            // Exploration: Random selection
            if (!connections.isEmpty()) {
                bestHost = connections.get(rand.nextInt(connections.size())).getOtherNode(getHost());
                System.out.println("Exploration selected host: " + bestHost.getAddress());
            }
        } else {
            // Exploitation: Select best Q-Value node
            for (Connection conn : connections) {
                DTNHost neighbor = conn.getOtherNode(getHost());
                double qValue = getQValue(msg.getId(), neighbor);
                System.out.println("Neighbor: " + neighbor.getAddress() + ", Q-Value: " + qValue);
                if (qValue > maxQValue) {
                    maxQValue = qValue;
                    bestHost = neighbor;
                }
            }
        }

        if (bestHost == null) {
            System.out.println("Fallback mechanism activated for message: " + msg.getId());
            bestHost = connections.get(0).getOtherNode(getHost()); // Fallback ke koneksi pertama
        }

        return bestHost;
    }

    private void updateQTable(String msgId, DTNHost nextHop, double reward) {
        String stateAction = msgId + "->" + (nextHop != null ? nextHop.getAddress() : "null");
        double oldQValue = qTable.getOrDefault(stateAction, 0.0);
        double newQValue = oldQValue + LEARNING_RATE * (reward + DISCOUNT_FACTOR * maxFutureQ(msgId) - oldQValue);
        qTable.put(stateAction, newQValue);
    }

    private double maxFutureQ(String msgId) {
        double maxQ = 0.0;
        List<Connection> connections = new ArrayList<>(this.getHost().getConnections());
        for (Connection conn : connections) {
            DTNHost neighbor = conn.getOtherNode(getHost());
            double qValue = getQValue(msgId, neighbor);
            maxQ = Math.max(maxQ, qValue);
        }
        return maxQ;
    }

    private double getQValue(String msgId, DTNHost neighbor) {
        return qTable.getOrDefault(msgId + "->" + neighbor.getAddress(), 0.0);
    }

    @Override
    public ActiveRouter replicate() {
        return new RLRouter(this.settings);  // Gunakan variabel instance settings
    }
}



