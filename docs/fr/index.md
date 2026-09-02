# Manuel Utilisateur 橘子岛

Bienvenue dans le manuel utilisateur 橘子岛. 橘子岛 est un client LLM BYOK (Bring Your Own Key) pour Android avec accès multi-fournisseurs, conversations à branchement non linéaire, appels d'outils agentiques, génération d'images et contrôle d'appareil à distance.

## Liens Rapides

### Pour Commencer

- **[Pour Commencer](getting-started.md)** — installer, configurer et envoyer votre premier message
- **[FAQ](faq.md)** — réponses aux questions courantes

### Fonctionnalités Principales

- **[Conversations](conversations.md)** — branchement non linéaire, opérations sur les messages, streaming, rendu markdown
- **[Fournisseurs API](provider.md)** — connexion à OpenAI, Anthropic, Google, DeepSeek, Ollama et points de terminaison personnalisés
- **[Modèles](models.md)** — activer/désactiver des modèles, alias, synchronisation des modèles par fournisseur
- **[Prompts Système](system-prompts.md)** — éditeur à trois sections, substitution de variables, changement par conversation
- **[Génération](generation.md)** — température, top P, tokens max, réflexion, pénalités de fréquence/présence
- **[Génération de Titres](title-generation.md)** — générer automatiquement les titres des conversations
- **[Transcription d'Images](transcription.md)** — pipeline image-texte pour les fournisseurs sans vision
- **[Génération d'Images](image-generation.md)** — génération texte-image comme outil de chat
- **[Apparence](appearance.md)** — mode thème, schéma de couleurs, couleur dynamique, style de schéma, effets de flou

### Outils Agentiques

- **[Aperçu](tools.md)** — comment fonctionne l'appel d'outils multi-tours
- **[Recherche Web](web-search.md)** — intégration DuckDuckGo Lite, Brave, Serper, Tavily, SearXNG
- **[Shell Distant (Conch)](shell.md)** — exécution de commandes chiffrées à distance, opérations sur fichiers, intégration MCP
- **[Sandbox](sandbox.md)** — environnement Alpine Linux local pour l'exécution isolée de commandes

### Gestion des Connaissances

- **[Recherche de Conversations](search.md)** — recherche par mots-clés et sémantique (RAG) dans l'historique de chat
- **[Embedding / RAG](embedding.md)** — configurer les modèles d'embedding pour la recherche sémantique
- **[Mémoire & Cache](memory.md)** — mémoire active, mémoires sauvegardées, mise en cache automatique

### Plus

- **[Modèles Locaux](local-model.md)** — exécuter des modèles GGUF sur l'appareil via llama.cpp
- **[Import PDF](pdf-import.md)** — extraire et envoyer des pages PDF aux modèles de vision
- **[Portabilité des Données](import-export.md)** — exporter/importer des fichiers .橘子岛, sauvegarde automatique, importer depuis Claude et ChatGPT
- **[Langue](language.md)** — basculer entre anglais, chinois, chinois traditionnel ou langue système
- **[À Propos](about.md)** — informations de version, mises à jour, options de documentation, liens, évaluation

---

## À Propos d'橘子岛

橘子岛 est un client Android BYOK pour les utilisateurs avancés d'IA :

- **Sans intermédiaire** : Connexions API directes, pas de télémétrie, pas de pistage
- **Stockage local** : Tout réside localement dans une base de données Room
- **Conversations non linéaires** : Modifiez n'importe quel message passé et explorez des branches alternatives
- **Agentique par défaut** : Appels d'outils multi-tours avec recherche web, génération d'images, exécution de code, shell, opérations sur fichiers et mémoire
- **Contrôle à distance** : Gérez des serveurs via le protocole chiffré Conch
- **Open source** : Licence MIT, [code source sur GitHub](https://github.com/chloemeadow0-code/Orange-Island)
