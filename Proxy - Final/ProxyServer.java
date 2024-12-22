import java.io.*;
import java.net.*;
import java.util.*;

public class ProxyServer {
    private static List<String> blockedKeywords = new ArrayList<>();
    private static final String CACHE_DIR = "cache";
    private static Map<String, String> cache = new HashMap<>(); // Cache en mémoire

    static { //Ce bloc s'exécute une seule fois, lors du chargement de la classe
        // Assurez-vous que le répertoire de cache existe
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }
    }
    private static long cacheValidity = 60 * 1000; // Par défaut, 1 minute

    // Mettre à jour cacheValidity lors du chargement de la configuration
    public static void loadCacheValidity(String configFilePath) {
        Properties config = loadConfiguration(configFilePath);
        String validityString = config.getProperty("cacheValidity", "60"); // Valeur par défaut : 60 secondes
        try {
            cacheValidity = Long.parseLong(validityString) * 1000; // Convertir en millisecondes
        } catch (NumberFormatException e) {
            System.out.println("Erreur lors du chargement de cacheValidity. Valeur par défaut utilisée.");
        }
    }

    // Charger les mots-clés bloqués depuis le fichier de configuration
    public static void loadBlockedKeywords(String configFilePath) {
        Properties config = loadConfiguration(configFilePath);
        String blockedKeywordsString = config.getProperty("url", "");

        if (!blockedKeywordsString.isEmpty()) {
            blockedKeywords = Arrays.asList(blockedKeywordsString.split(","));
        }
    }

    // Fonction pour charger le fichier de configuration
    public static Properties loadConfiguration(String filePath) {  // Propreties une structure spécialement conçue pour stocker et manipuler des paramètres de configuration sous forme de paires clé-valeur
        Properties properties = new Properties(); //utilisé pour stocker les paires clé-valeur contenues dans le fichier de configuration.
        try (InputStream input = new FileInputStream(filePath)) {
            properties.load(input);
        } catch (IOException e) {
            System.out.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }
        return properties;
    }

    // Vérifier si l'URL est bloquée
    private static boolean isBlockedRequest(String requestedUrl) {
        if (requestedUrl == null) return false;
        for (String keyword : blockedKeywords) {
            if (requestedUrl.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // Filtrer le contenu de la réponse
    private static String filterResponseContent(String responseContent) {
        return responseContent.replaceAll("test", "proxy");
    }

    // Sauvegarder dans le cache (sur disque)
    private static void saveToCacheFile(String requestedUrl, String content) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String fileName = CACHE_DIR + File.separator + requestedUrl.hashCode() + "_" + timestamp;
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {  //Crée un fichier unique pour chaque URL et Écrit le contenu dans ce fichier
                writer.write(content);
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de l'enregistrement dans le cache : " + e.getMessage());
        }
    }

    // Charger depuis le cache (sur disque)
    private static String loadFromCacheFile(String requestedUrl) {
        try {
            File cacheDir = new File(CACHE_DIR);
            File[] files = cacheDir.listFiles((dir, name) -> name.startsWith(String.valueOf(requestedUrl.hashCode())));
            if (files != null && files.length > 0) {
                for (File file : files) {
                    String[] parts = file.getName().split("_");
                    if (parts.length > 1) {
                        long fileTimestamp = Long.parseLong(parts[1]);
                        long currentTime = System.currentTimeMillis();
    
                        if (currentTime - fileTimestamp <= cacheValidity) {
                            StringBuilder content = new StringBuilder();
                            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    content.append(line).append("\n");
                                }
                            }
                            return content.toString();
                        } else {
                            if (!file.delete()) {
                                System.out.println("Impossible de supprimer le fichier expiré : " + file.getName());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de la lecture du cache : " + e.getMessage());
        }
        return null;
    }
    

    // Nettoyer les fichiers du cache expirés
    private static void startCacheCleaner() {
        Runnable cacheCleaner = () -> {
            while (true) {
                try {
                    Thread.sleep(1000); // Exécution toutes les secondes
    
                    File cacheDir = new File(CACHE_DIR);
                    long currentTime = System.currentTimeMillis();
    
                    for (File file : Objects.requireNonNull(cacheDir.listFiles())) {
                        String[] parts = file.getName().split("_");
                        if (parts.length > 1) {
                            try {
                                long fileTimestamp = Long.parseLong(parts[1]);
                                if (currentTime - fileTimestamp > cacheValidity) {
                                    String key = parts[0];
                                    cache.remove(key);
                                    System.out.println("Entrée supprimée de la mémoire pour la clé : " + key);
    
                                    if (file.delete()) {
                                        System.out.println("Fichier supprimé du cache : " + file.getName() + " (expiré)");
                                    } else {
                                        System.out.println("Impossible de supprimer le fichier de cache : " + file.getName());
                                    }
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Erreur lors du traitement du timestamp pour le fichier : " + file.getName());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    System.out.println("Erreur dans le nettoyeur de cache : " + e.getMessage());
                }
            }
        };
        new Thread(cacheCleaner).start();
    }
    

    // Gérer la requête du client
    public static void handleRequest(Socket clientSocket, String targetHost, int targetPort) {
        try {
            BufferedReader clientInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter clientOutput = new PrintWriter(clientSocket.getOutputStream(), true);

            String clientRequestLine = clientInput.readLine();
            System.out.println("Requête du client: " + clientRequestLine);

            String requestedUrl = null;
            if (clientRequestLine != null && clientRequestLine.startsWith("GET")) {
                String[] requestParts = clientRequestLine.split(" ");
                if (requestParts.length > 1) {
                    requestedUrl = requestParts[1];
                }
            }

            if (requestedUrl != null && isBlockedRequest(requestedUrl)) {
                System.out.println("Requête bloquée : " + requestedUrl);
                clientOutput.println("HTTP/1.1 403 Forbidden\r\n\r\n");
                clientOutput.println("<html><body><h1>403 Forbidden</h1><p>Access to this URL is blocked by the proxy.</p></body></html>");
                return;
            }

            String cachedResponse = loadFromCacheFile(requestedUrl);

            if (cachedResponse != null) {
                System.out.println("Réponse récupérée du cache pour : " + requestedUrl);
                clientOutput.println(cachedResponse);
            } else {
                Socket serverSocket = new Socket(targetHost, targetPort);
                BufferedReader serverInput = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                PrintWriter serverOutput = new PrintWriter(serverSocket.getOutputStream(), true);

                if (clientRequestLine != null) {
                    serverOutput.println(clientRequestLine);
                }

                String headerLine;
                while ((headerLine = clientInput.readLine()) != null && !headerLine.isEmpty()) {
                    if (headerLine.startsWith("User-Agent:")) {
                        headerLine = "User-Agent: ProxyServer";
                    }
                    serverOutput.println(headerLine);
                }
                serverOutput.println("");

                StringBuilder serverResponse = new StringBuilder();
                String serverResponseLine;
                while ((serverResponseLine = serverInput.readLine()) != null) {
                    serverResponse.append(serverResponseLine).append("\n");
                }

                String filteredResponse = filterResponseContent(serverResponse.toString());
                clientOutput.println(filteredResponse);

                if (requestedUrl != null) {
                    saveToCacheFile(requestedUrl, filteredResponse);
                }

                serverInput.close();
                serverOutput.close();
                serverSocket.close();
            }

            clientInput.close();
            clientOutput.close();
            clientSocket.close();

        } catch (IOException e) {
            System.out.println("Erreur lors de la gestion de la requête : " + e.getMessage());
        }
    }

    // Démarrer le serveur proxy
    public static void startServer(int proxyPort, String targetHost, int targetPort) {
        try {
            ServerSocket serverSocket = new ServerSocket(proxyPort);
            System.out.println("Serveur proxy démarré sur le port " + proxyPort);

            startCacheCleaner();  // Démarre le nettoyage du cache

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connecté : " + clientSocket.getInetAddress());

                new Thread(() -> handleRequest(clientSocket, targetHost, targetPort)).start();
            }
        } catch (IOException e) {
            System.out.println("Erreur lors du démarrage du serveur proxy : " + e.getMessage());
        }
    }
    // Méthode pour afficher le contenu du cache en mémoire
    public static void displayCacheContent() {
        if (cache.isEmpty()) {
            System.out.println("Le cache est vide.");
        } else {
            System.out.println("Contenu du cache en mémoire :");
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                System.out.println("URL Hash : " + entry.getKey());
                System.out.println("Contenu : " + entry.getValue());
                System.out.println("-------------");
            }
        }
    }

}
