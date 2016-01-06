package nameserver;

import cli.Command;
import cli.Shell;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activity.InvalidActivityException;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
//import oracle.jrockit.jfr.tools.ConCatRepository;
import org.bouncycastle.asn1.cms.Time;

import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserverCli, INameserver, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private ConcurrentHashMap<String, INameserver> subdomains;
    private ConcurrentHashMap<String, String> addresses;
    private Registry reg;

    private final ExecutorService threadPool;

    private Shell shell;

    /**
     * @param componentName the name of the component - represented in the
     * prompt
     * @param config the configuration to use
     * @param userRequestStream the input stream to read user input from
     * @param userResponseStream the output stream to write the console output
     * to
     */
    public Nameserver(String componentName, Config config,
            InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        this.subdomains = new ConcurrentHashMap<>();
        this.addresses = new ConcurrentHashMap<>();

        this.reg = null;
        // TODO

        threadPool = Executors.newCachedThreadPool();

        shell = new Shell(componentName, userRequestStream, userResponseStream);
        shell.register(this);
    }

    private void print(String msg, boolean time) {
        this.userResponseStream.println((time ? new Date().toString().split(" ")[3] : "") + " : " + msg);
    }

    @Override
    public void run() {
        // TODO
        this.print("Starting nameserver: " + componentName, true);
        if (!config.listKeys().contains("domain")) {
            try {
                this.reg = LocateRegistry.createRegistry(config.getInt("registry.port"));
                reg.bind(config.getString("root_id"), (INameserver) UnicastRemoteObject.exportObject(this, 0));
                print("Created registry and bound root", true);
            } catch (RemoteException ex) {
                this.print("RemoteException: Could not create registry", true);
            } catch (AlreadyBoundException ex) {
                this.print("AlreadyBoundException: Could not bind remote object", true);
            }
        } else {
            String domain = config.getString("domain");
            try {
                Registry reg = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
                INameserver ns = (INameserver) reg.lookup(config.getString("root_id"));
                INameserver me = (INameserver) UnicastRemoteObject.exportObject(this, 0);
                ns.registerNameserver(config.getString("domain"), me, me);

            } catch (NotBoundException ex) {
                this.print("Rootserver hasn't been bound", true);
            } catch (AlreadyRegisteredException ex) {
                this.print("Nameserver already registered", true);
            } catch (InvalidDomainException ex) {
                this.print("Missing one or more nameservers for required zones", true);
            } catch (RemoteException ex) {
                if (ex.getCause() instanceof AlreadyRegisteredException) {
                    this.print(ex.getCause().getMessage(), true);
                } else {
                    this.print(ex.getCause().getMessage(), true);
                    this.print("RemoteException: Could not access registry", true);
                }
//                ex.printStackTrace();
            }
        }

        threadPool.execute(new Thread(shell));
    }

    @Override
    public void registerNameserver(String domain, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String[] parts = domain.split("\\.");
        if (subdomains.containsKey(parts[parts.length - 1])) {
            if (parts.length == 1) {
                throw new AlreadyRegisteredException(domain + " has already been registered");
            } else {
                String[] subDomainParts = new String[parts.length - 2];
                for (int i = 0; i < subDomainParts.length; i++) {
                    subDomainParts[i] = parts[i];
                }
                String subDomain = String.join(".", subDomainParts);
                this.print("Registering nameserver for domain " + domain, true);
                subdomains.get(parts[parts.length - 1]).registerNameserver(subDomain, nameserver, nameserverForChatserver);
            }
        } else if (parts.length == 1) {
            subdomains.put(domain, nameserver);
            this.print("Registered nameserver for domain " + domain, true);
        } else {
            throw new InvalidDomainException(parts[parts.length - 1] + " hasn't been registered yet");
        }
    }

    @Override
    public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String[] parts = username.split("\\.");
        if (subdomains.containsKey(parts[parts.length - 1])) {
            String[] subDomainParts = new String[parts.length - 1];
            for (int i = 0; i < subDomainParts.length; i++) {
                subDomainParts[i] = parts[i];
                this.print("-------------" + i + "", true);
            }
            String subDomain = String.join(".", subDomainParts);
            this.print("Forwarding registering of " + username + " to " + parts[parts.length - 1], true);
            subdomains.get(parts[parts.length - 1]).registerUser(subDomain, address);
        } else if (parts.length == 1) {
            addresses.put(parts[0], address);
            this.print("Registering user " + parts[0] + " for address " + address, true);
        } else {
            throw new InvalidDomainException(parts[parts.length - 1] + " could not be resolved");
        }
    }

    @Override
    public INameserverForChatserver getNameserver(String zone) throws RemoteException {
        if (subdomains.containsKey(zone)) {
            return (INameserverForChatserver) subdomains.get(zone);
        }
        throw new RemoteException("Zone could not be resolved");
    }

    @Override
    public String lookup(String username) throws RemoteException {
        if (addresses.containsKey(username)) {
            return addresses.get(username);
        }
        throw new RemoteException("Username could not be resolved");
    }

    @Override
    @Command
    public String nameservers() throws IOException {
        // TODO Auto-generated method stub
        StringBuilder strb = new StringBuilder();
        int c = 0;
        for (String str : subdomains.keySet()) {
            strb.append(++c).append(". ").append(str).append("\n");
        }
        return strb.toString();
    }

    @Override
    @Command
    public String addresses() throws IOException {
        // TODO Auto-generated method stub
        StringBuilder strb = new StringBuilder();
        int c = 0;
        for (Map.Entry<String, String> me : addresses.entrySet()) {
            strb.append(++c).append(". ").append(me.getKey()).append(" ").append(me.getValue()).append("\n");
        }
        return strb.toString();
    }

    @Override
    @Command
    public String exit() throws IOException {
        // TODO Auto-generated method stub
        if (!config.listKeys().contains("domain")) {
            try {
                UnicastRemoteObject.unexportObject(reg, true);
            } catch (NoSuchObjectException ex) {

            }
        }
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ex) {

        }
        threadPool.shutdownNow();
        return "Shutting down nameserver: " + componentName;
    }

    @Override
    public String toString() {
        return componentName;
    }

    /**
     * @param args the first argument is the name of the {@link Nameserver}
     * component
     */
    public static void main(String[] args) throws RemoteException {
        Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
                System.in, System.out);
        // TODO: start the nameserver
        nameserver.run();

    }

}
