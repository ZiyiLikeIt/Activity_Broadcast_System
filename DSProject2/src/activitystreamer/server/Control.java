/**
 * This class serve a server to monitor and save all parameters in a server.
 * 
 * @author modified by
 *         group666:
 *         Shujing Xiao
 *             (Login name: shujingx Email: shujingx@student.unimelb.edu.au)
 *         Ziyi Xiong
 *             (Login name: zxiong1 Email: zxiong1@student.unimelb.edu.au)
 *         Ziyi Xiong
 *             (Login name: zxiong1 Email: zxiong1@student.unimelb.edu.au)
 *         Zhengqing Zhu
 *             (Login name: zhengqingz Email: zhengqingz@student.unimelb.edu.au)
 */

package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;

public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ArrayList<Connection> connections;
    private static boolean term = false;
    private static Listener listener;
    private static String serverID;
    private static ArrayList<Map<String, String>> interconnectedServers;
    private static ArrayList<String> authenticatedServers; // <socketAddress>
    private static Map<String, String> registeredClients; // <username, secret>
    private static Map<String, Connection> registeringClients; // <username, con>
    private static Map<String, String> loggedinClients; // <username:secret, socketAddress>
    private static ArrayList<String> loggedinAnonymous; // <socketAddress>

    protected static Control control = null;

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    public Control() {
        // initialize the connections array
        connections = new ArrayList<Connection>();
        // initialize the interconnected-servers list
        interconnectedServers = new ArrayList<Map<String, String>>();
        // initialize the serverID
        serverID = Settings.nextSecret();
        // initialize the authenticated-servers list
        authenticatedServers = new ArrayList<String>();
        // initialize the registered-clients map
        registeredClients = new HashMap<String, String>();
        // initialize the registering-clients map
        registeringClients = new HashMap<String, Connection>();
        // initialize the loggedin clients with username
        loggedinClients = new HashMap<String, String>();
        // initialize the anonymous loggedin clients
        loggedinAnonymous = new ArrayList<String>();
        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
        initiateConnection();
        start();
    }

    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null
                && ControlSolution.hasSecret()) {
            try {
                outgoingConnection(new Socket(Settings.getRemoteHostname(),
                        Settings.getRemotePort()));
            } catch (IOException e) {
                log.error("failed to make connection to "
                        + Settings.getRemoteHostname() + ":"
                        + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    /*
     * Processing incoming messages from the connection. Return true if the
     * connection should close.
     */
    public synchronized boolean process(Connection con, String msg) {
        switch (ControlSolution.getCommandName(con, msg)) {
        case "AUTHENTICATE":
            con.setServer(true);
            return ControlSolution.receiveAuthenticate(con, msg);
        case "AUTHENTICATION_FAIL":
            return ControlSolution.receiveAuthenticationFail(con, msg);
        case "SERVER_ANNOUNCE":
            return ControlSolution.receiveServerAnnounce(con, msg);
        case "REGISTER":
            return ControlSolution.receiveRegister(msg, con);
        case "LOCK_REQUEST":
            return ControlSolution.receiveLockRequest(msg, con);
        case "LOCK_DENIED":
            return ControlSolution.receiveLockDenied(msg, con);
        case "LOCK_ALLOWED":
            return ControlSolution.receiveLockAllowed(msg, con);
        case "LOGIN":
            return ControlSolution.receiveLogin(msg, con);
        case "LOGOUT":
            return ControlSolution.receiveLogout(con);
        case "ACTIVITY_BROADCAST":
            return ControlSolution.receiveActivityBroadcast(con, msg);
        case "ACTIVITY_MESSAGE":
            return ControlSolution.receiveActivityMessage(con, msg);
        case "INVALID_MESSAGE":
            return ControlSolution.receiveInvalidMessage(con, msg);
        case "": // received message do not have command field
            return true;
        default: // unknown command
            String response = ControlSolution.sendInvalidMessage(
                    "received message contains unknown command");
            con.writeMsg(response);
            return true;
        }
    }

    /*
     * A new incoming connection has been established, and a reference is returned
     * to it
     */
    public synchronized Connection incomingConnection(Socket s)
            throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);
        return c;

    }

    /*
     * A new outgoing connection has been established, and a reference is returned
     * to it
     */
    public synchronized Connection outgoingConnection(Socket s)
            throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        c.writeMsg(ControlSolution.sendAuthenticate());
        c.setServer(true);
        connections.add(c);
        authenticatedServers.add(Settings.socketAddress(s));
        return c;
    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term)
            connections.remove(con);
    }

    public synchronized void addAuthenServer(String serverSendingAddress) {
        if (!term)
            authenticatedServers.add(serverSendingAddress);
    }

    public synchronized void removeAuthenServer(String serverSendingAddress) {
        if (!term)
            authenticatedServers.remove(serverSendingAddress);
    }

    public synchronized void addConnnectedServer(
            Map<String, String> serverState) {
        if (!term)
            interconnectedServers.add(serverState);
    }
    
    public synchronized void removeConnectedServer(
            Map<String, String> serverState) {
        if (!term)
            interconnectedServers.remove(serverState);
    }

    public synchronized void addRegisteringClient(String username,
            Connection con) {
        if (!term)
            registeringClients.put(username, con);
    }

    public synchronized void removeRegisteringClient(String username) {
        if (!term)
            registeringClients.remove(username);
    }

    public synchronized void addRegisteredClient(String username,
            String secret) {
        if (!term)
            registeredClients.put(username, secret);
    }

    public synchronized void removeRegisteredClient(String username) {
        if (!term)
            registeredClients.remove(username);
    }

    public synchronized void addLoggedinClient(String usernameSecret,
            String socketAddress) {
        if (!term)
            loggedinClients.put(usernameSecret, socketAddress);
    }

    public synchronized void removeLoggedinClient(String usernameSecret) {
        if (!term)
            loggedinClients.remove(usernameSecret);
    }

    public synchronized void addLoggedinAnonymous(String socketAddress) {
        if (!term)
            loggedinAnonymous.add(socketAddress);
    }

    public synchronized void removeLoggedinAnonymous(String socketAddress) {
        if (!term)
            loggedinAnonymous.remove(socketAddress);
    }

    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval()
                + " milliseconds");
        while (!term) {
            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
                log.debug("doing activity");
                term = doActivity();
            }

        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);
    }

    public boolean doActivity() {
        for (Connection c : connections) {
            if (c.isServer())
                c.writeMsg(ControlSolution.sendServerAnnounce());
        }
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }

    public final ArrayList<Map<String, String>> getInterconnectedServers() {
        return interconnectedServers;
    }

    public final ArrayList<String> getAuthenticatedServers() {
        return authenticatedServers;
    }

    public final Map<String, String> getRegisteredClients() {
        return registeredClients;
    }

    public final Map<String, Connection> getRegisteringClients() {
        return registeringClients;
    }

    public final Map<String, String> getLoggedinClients() {
        return loggedinClients;
    }

    public final ArrayList<String> getLoggedinAnonymous() {
        return loggedinAnonymous;
    }

    public final String getServerID() {
        return serverID;
    }
}