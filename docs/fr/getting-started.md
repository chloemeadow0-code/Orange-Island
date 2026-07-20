# Pour Commencer

Ce guide vous accompagne dans l'installation d'橘子岛, l'ajout de votre première clé API et l'envoi de votre premier message.

## Installation

### Depuis F-Droid (Recommandé)

橘子岛 est disponible sur F-Droid, le magasin d'applications Android open source.

1. Installez [F-Droid](https://f-droid.org/) sur votre appareil
2. Ouvrez F-Droid, recherchez **橘子岛**
3. Appuyez sur **Installer**

### Depuis les Versions GitHub

1. Visitez la [page des Versions](https://github.com/orangeisland/app/releases)
2. Téléchargez le dernier fichier `.apk`
3. Ouvrez le fichier sur votre appareil et confirmez l'installation quand demandé

### Compiler depuis la Source

Si vous préférez compiler vous-même :

1. Clonez le dépôt :
   ```
   git clone https://github.com/orangeisland/app.git
   ```
2. Ouvrez le projet dans [Android Studio](https://developer.android.com/studio) (Ladybug ou plus récent)
3. Synchronisez Gradle et compilez

Prérequis : Android SDK 34+, JDK 17+.

---

## Premier Lancement

Lorsque vous ouvrez 橘子岛 pour la première fois, vous verrez un écran d'accueil avec une zone de texte. Avant de pouvoir discuter, vous devez configurer un fournisseur et une clé API.

### Étape 1 : Ajouter une Clé API

1. Appuyez sur l'icône **Paramètres** (engrenage en bas à droite) dans la barre de navigation
2. Sous **Services**, appuyez sur **Fournisseur**
3. Sélectionnez un fournisseur dans la liste (par ex., **OpenAI**, **Anthropic**, **Google**)
4. Appuyez sur **Ajouter une Nouvelle Clé**
5. Saisissez un nom pour votre clé (par ex., "Personnelle") et collez votre clé API
6. Appuyez sur **Ajouter**

??? tip "Où puis-je obtenir une clé API ?"
    - **Google Gemini** : [Google AI Studio](https://aistudio.google.com/apikey) — offre gratuite disponible
    - **OpenAI** : [Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic** : [Console API Keys](https://console.anthropic.com/)
    - **DeepSeek** : [Platform](https://platform.deepseek.com/)
    - **OpenRouter** : [Keys page](https://openrouter.ai/keys)

    Consultez la page [Fournisseurs API](provider.md) pour plus de détails sur chaque fournisseur.

### Étape 2 : Synchroniser les Modèles

1. Retournez dans Paramètres et appuyez sur **Modèles** (sous **Services**)
2. Appuyez sur **Synchroniser depuis Tous les Fournisseurs**
3. 橘子岛 récupère la dernière liste de modèles pour tous les fournisseurs configurés
4. Une fois synchronisé, appuyez sur un modèle pour le définir comme **Modèle par Défaut**

### Étape 3 : Envoyer Votre Premier Message

1. Appuyez sur la **flèche retour** pour revenir à l'écran de chat
2. Tapez un message dans le champ de saisie en bas
3. Appuyez sur **Envoyer** (icône d'avion en papier)

Le modèle diffusera sa réponse en temps réel.

---

## Disposition de l'Application

橘子岛 a une disposition épurée centrée autour de l'écran de chat :

### Barre Supérieure

- **Titre de la conversation** — affiche le nom de la conversation actuelle (appuyez pour renommer)
- **Menu hamburger** (:material-menu:) — ouvre le tiroir des conversations
- **Menu contextuel** (:material-dots-vertical:) — paramètres par conversation (modèle, prompt système, paramètres de génération)

### Tiroir des Conversations

Appuyez sur le **menu hamburger** ou balayez vers la droite depuis le bord gauche pour ouvrir :

- **Barre de recherche** — trouver des conversations passées par mot-clé ou recherche sémantique
- **Liste des conversations** — toutes les conversations, les plus récentes en premier
- **Paramètres** (:material-cog:) — configurer les fournisseurs, modèles, prompts et plus
- **Nouveau Chat** — démarrer une nouvelle conversation

### Écran de Chat

- **Zone de messages** — historique de conversation défilable avec rendu markdown
- **Barre inférieure** — saisie de texte, sélecteur de modèle, bouton de pièce jointe (+) et bouton d'envoi

---

## Prochaines Étapes

- [Configurer les prompts système](system-prompts.md) pour personnaliser le comportement du modèle
- [Configurer la recherche web](web-search.md) pour un accès internet en direct
- [Explorer les outils agentiques](tools.md) — exécution shell, opérations sur fichiers et mémoire
- [Importer des données](import-export.md) depuis Claude ou ChatGPT
- [Exécuter des modèles locaux](local-model.md) pour une utilisation hors ligne
