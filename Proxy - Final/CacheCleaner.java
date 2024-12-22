import java.io.File;

public class CacheCleaner {

    // Fonction pour supprimer les fichiers dans le dossier cache
    public static void clearCacheFolder(String cacheDirectory) {
        File cacheDir = new File(cacheDirectory);

        // Vérifier si le répertoire existe et c'est un répertoire
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            // Lister tous les fichiers dans le répertoire
            File[] files = cacheDir.listFiles();

            if (files != null) {
                for (File file : files) {
                    // Si le fichier est un fichier régulier, le supprimer
                    if (file.isFile()) {
                        if (file.delete()) {
                            System.out.println("Fichier supprimé : " + file.getName());
                        } else {
                            System.out.println("Échec de la suppression du fichier : " + file.getName());
                        }
                    }
                }
            }
        } else {
            System.out.println("Le répertoire de cache n'existe pas ou n'est pas un répertoire valide.");
        }
    }

    // Méthode main pour tester la suppression des fichiers du cache
    public static void main(String[] args) {
        String cacheDirectory = "cache";  // Remplacer par le chemin de votre dossier de cache

        // Appeler la fonction pour supprimer les fichiers du cache
        clearCacheFolder(cacheDirectory);
    }
}
