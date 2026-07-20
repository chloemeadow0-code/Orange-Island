# Outils Agentiques

Les modèles d'橘子岛 peuvent utiliser des outils de manière autonome — ils décident quoi rechercher, exécuter, lire ou mémoriser sans que vous ayez besoin de déclencher manuellement chaque action. Les outils fonctionnent en **boucles multi-tours** : le modèle peut appeler un outil, lire le résultat, puis décider d'appeler un autre outil ou de répondre.

## Comment Fonctionne l'Appel d'Outils

1. Vous envoyez un message
2. Le modèle décide qu'il a besoin d'informations ou d'une action externe
3. Il émet un **appel d'outil** — une requête structurée avec un nom d'outil et des arguments
4. 橘子岛 exécute l'outil sur l'appareil ou sur un serveur distant
5. Le résultat est renvoyé au modèle
6. Le modèle peut appeler un autre outil ou produire une réponse finale

Cette boucle peut se répéter plusieurs fois au sein d'un seul tour de message.

## Outils Disponibles

### Recherche Web

Recherchez sur internet et récupérez des pages web. Le modèle peut rechercher des informations actuelles, vérifier des faits ou récupérer de la documentation.

- **Fournisseurs** : DuckDuckGo Lite (par défaut, sans clé), Brave, Serper, Tavily, SearXNG
- **Configuration** : Paramètres → Recherche Web
- **Guide** : [Recherche Web](web-search.md)

### Génération d'Images

Générez des images à partir de prompts textuels en utilisant un modèle texte-image dédié. Les images s'affichent en ligne dans la conversation et peuvent être vues en plein écran.

- **Fournisseur** : BYOK — utilise votre propre clé API et URL de base, découplé du modèle de chat
- **Configuration** : Paramètres → Génération d'Images
- **Guide** : [Génération d'Images](image-generation.md)

### Exécution de Code

Exécutez du code dans un environnement isolé :

- **Gemini Code Execution** — exécution de code intégrée pour les modèles Gemini (aucune configuration)
- **Sandbox** — environnement Alpine Linux local via PRoot, avec gestion des paquets et accès aux fichiers SAF

### Shell Distant

Exécutez des commandes sur des machines distantes via le protocole [Conch](https://github.com/orangeisland/conch). Le modèle peut vérifier l'état du serveur, gérer des fichiers ou exécuter des scripts.

- **Protocole** : Chiffré de bout en bout (ECDH + AES-256-GCM)
- **Configuration** : Paramètres → Shell
- **Guide** : [Shell Distant](shell.md)

### Opérations sur Fichiers

Lisez, écrivez, modifiez, recherchez par glob et grep des fichiers sur des appareils distants via le protocole Conch. Le modèle peut manipuler directement les systèmes de fichiers distants.

!!! note
    Les opérations sur fichiers nécessitent un appareil shell Conch configuré. Voir [Shell Distant](shell.md) pour la configuration.

### Mémoire

Stockage de connaissances persistant qui s'étend sur les conversations :

- **Mémoire Active** — toujours incluse dans chaque appel API. Utilisez-la pour des faits, préférences ou contexte que le modèle doit toujours connaître.
- **Mémoires Sauvegardées** — une collection de fichiers de mémoire nommés que le modèle peut rechercher, lire, écrire et modifier via des appels d'outils.

Voir [Mémoire & Cache](memory.md) pour plus de détails.

### Recherche de Conversations

Le modèle peut rechercher dans votre historique de conversations passé en utilisant des méthodes par mot-clé ou sémantiques (RAG). Cela lui permet de référencer des discussions précédentes sans que vous ayez besoin de les trouver et partager manuellement.

Voir [Recherche de Conversations](search.md) pour la configuration.

---

## Interface des Outils dans le Chat

Lorsqu'un outil est appelé, vous le verrez en ligne dans la conversation :

<div class="grid cards" markdown>

- **:material-progress-wrench: Bannière d'Appel d'Outil**

    ---

    Affiche le nom de l'outil et un bref statut (par ex., :material-magnify: "Recherche 'dernières actualités IA' sur le web").

- **:material-check-circle: Résultat de l'Outil**

    ---

    Après l'exécution, affiche le résultat formaté ou le résumé (par ex., "5 résultats trouvés pour 'dernières actualités IA'").

</div>

### Détails Dépliables

Appuyez sur un appel d'outil pour le déplier et voir :

- **Arguments** — les paramètres exacts envoyés à l'outil
- **Résultat** — la sortie brute de l'exécution de l'outil
- **Statut** — succès, échec ou résultats partiels

### Appels Échoués

Si un appel d'outil échoue, le modèle est notifié de l'erreur et peut réessayer ou s'ajuster. Vous verrez une bannière rouge avec le message d'erreur.

---

## Autorisations des Outils

Vous contrôlez les outils auxquels le modèle peut accéder :

| Paramètre | Emplacement | Par Défaut |
|-----------|-------------|------------|
| Recherche Web | Paramètres → Recherche Web | Désactivé |
| Shell | Paramètres → Shell | Désactivé |
| Mémoire (Sauvegardée) | Paramètres → Mémoire → Accéder aux Mémoires Sauvegardées | Désactivé |
| Mémoire (Active) | Paramètres → Mémoire → Accéder à la Mémoire Active | Désactivé |
| Conversations Passées | Paramètres → Mémoire → Accéder aux Conversations Passées | Désactivé |
| Recherche de Conversations | Paramètres → Recherche de Conversations | Activé* |

*La capacité du modèle à rechercher dans les conversations dépend d'un modèle d'embedding configuré. Sans cela, seule la recherche par mot-clé est disponible.

---

## Boucles d'Outils Multi-Tours

Le modèle peut enchaîner plusieurs appels d'outils. Par exemple :

1. Utilisateur : "Quelle est la dernière version du noyau Linux et mon serveur l'exécute-t-il ?"
2. Le modèle appelle `web_search("dernière version du noyau Linux")`
3. Le modèle appelle `shell_execute("uname -r", appareil="mon-serveur")`
4. Le modèle compare les résultats et répond

Chaque appel d'outil et son résultat apparaissent comme des éléments en ligne séparés dans la conversation avant la réponse textuelle finale.
