# Shell Distant (Conch)

橘子岛 peut exécuter des commandes sur des machines distantes via le protocole [Conch](https://github.com/orangeisland/conch) — un shell sécurisé chiffré de bout en bout conçu pour les agents IA.

## Comment Ça Marche

```text
橘子岛 (Android)  ──ECDH + AES-256-GCM──▶  Serveur Conch (Linux/macOS/Windows)
                                           │
                                           ├── Exécuter des commandes
                                           ├── Lire/écrire/modifier des fichiers
                                           ├── Recherche glob et grep
                                           └── Retourner les résultats
```

Le modèle décide quand utiliser le shell — il peut vérifier l'état du serveur, gérer des fichiers, exécuter des scripts ou résoudre des problèmes de manière autonome.

## Sécurité

Conch utilise un chiffrement fort et des protections anti-abus :

- **Échange de clés ECDH** — clés éphémères par session
- **Chiffrement AES-256-GCM** — tout le trafic est chiffré
- **Signature HMAC-SHA256** — intégrité des messages vérifiée
- **Limitation de débit par seau à jetons** — prévient les abus
- **Anti-rejeu basé sur les nonces** — chaque requête est unique

!!! note
    Les commandes s'exécutent avec les permissions de l'utilisateur qui exécute le serveur Conch. Utilisez un compte utilisateur restreint pour les environnements sensibles.

---

## Configuration

### Étape 1 : Déployer le Serveur Conch

Déployez le serveur Conch sur votre machine cible. Consultez le [dépôt Conch](https://github.com/orangeisland/conch) pour les instructions d'installation.

### Étape 2 : Ajouter un Appareil dans 橘子岛

1. Allez dans **Paramètres → Shell**
2. Activez **Outil Shell**
3. Appuyez sur **Ajouter un Appareil**
4. Choisissez le type d'appareil : **Conch** ou **SSH**
5. Remplissez les détails de l'appareil :

=== "Conch"

    | Champ | Description | Exemple |
    |-------|-------------|---------|
    | **Nom** | Nom d'affichage pour cet appareil | `Serveur de Build` |
    | **Description** | Note optionnelle sur cette machine | `Machine Ubuntu du bureau` |
    | **URL du Serveur** | Point de terminaison du serveur Conch (hôte:port) | `http://192.168.1.100:14216` |
    | **Clé API** | Jeton d'authentification | Depuis la configuration du serveur Conch |
    | **Délai d'Expiration** | Délai d'expiration des commandes en secondes | `30` |

=== "SSH"

    | Champ | Description | Exemple |
    |-------|-------------|---------|
    | **Nom** | Nom d'affichage pour cet appareil | `Serveur VPS` |
    | **Description** | Note optionnelle sur cette machine | `Serveur web de production` |
    | **Hôte** | Nom d'hôte SSH ou adresse IP | `192.168.1.200` |
    | **Port** | Port SSH | `22` |
    | **Utilisateur** | Nom d'utilisateur SSH | `root` |
    | **Mot de Passe** | Mot de passe SSH | Votre mot de passe SSH |

Appuyez sur **Ajouter** pour enregistrer.

### Étape 3 : Utiliser

Une fois configuré, le modèle peut accéder à l'appareil. Il n'y a pas de déclencheur manuel — le modèle découvre automatiquement les appareils shell disponibles et les appelle quand c'est approprié.

---

## Support Multi-Appareils

Ajoutez plusieurs appareils shell pour permettre au modèle de travailler sur plusieurs machines :

- **Serveur de build** — compiler et tester du code
- **Labo domestique** — gérer des services auto-hébergés
- **VM de développement** — modifier du code et exécuter des scripts

Chaque appareil est configuré indépendamment avec son propre nom, URL et identifiants. Le modèle peut les distinguer et choisir le bon appareil pour chaque tâche.

---

## Opérations Disponibles

### Exécution de Commandes (`shell_execute`)

Exécutez n'importe quelle commande shell et recevez stdout, stderr et le code de sortie.

### Opérations sur Fichiers

| Outil | Fonction |
|-------|----------|
| `file_read` | Lire un fichier depuis le système de fichiers distant |
| `file_write` | Écrire ou écraser un fichier |
| `file_edit` | Effectuer des remplacements de chaînes exacts dans un fichier |
| `file_glob` | Trouver des fichiers correspondant à un motif glob |
| `file_grep` | Rechercher dans le contenu des fichiers avec des expressions régulières |

Toutes les opérations sur fichiers passent par le canal Conch chiffré.

---

## Intégration MCP

Conch peut également servir de **serveur MCP pour Claude Desktop**. Si vous utilisez Claude Code ou un autre client MCP, vous pouvez configurer Conch comme fournisseur d'outils pour l'accès distant aux fichiers et au shell depuis votre bureau.

Consultez la [documentation Conch](https://github.com/orangeisland/conch) pour les instructions de configuration MCP.

---

## Dépannage

### L'appareil apparaît comme indisponible
- Vérifiez que le serveur Conch est en cours d'exécution
- Vérifiez que l'URL est accessible depuis votre appareil Android
- Vérifiez les règles de pare-feu sur le serveur

### Les commandes expirent
- Augmentez le délai d'expiration dans les paramètres de l'appareil
- Vérifiez que la commande n'est pas bloquée (nécessite une entrée utilisateur, etc.)

### L'authentification échoue
- Vérifiez que la clé API correspond à la configuration du serveur
- Régénérez les clés si nécessaire
