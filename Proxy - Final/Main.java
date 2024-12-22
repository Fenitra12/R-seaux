import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        ProxyServer.loadBlockedKeywords("proxyconfig.conf");
        ProxyServer.loadCacheValidity("proxyconfig.conf");
        Properties config = ProxyServer.loadConfiguration("proxyconfig.conf");
    
        int proxyPort = Integer.parseInt(config.getProperty("proxyPort", "9090"));
        String targetHost = config.getProperty("targetHost", "localhost");
        int targetPort = Integer.parseInt(config.getProperty("targetPort", "81"));
    
        // Afficher le contenu du cache avant de démarrer le serveur
        ProxyServer.displayCacheContent();
    
        ProxyServer.startServer(proxyPort, targetHost, targetPort);
    }
    
    
}
