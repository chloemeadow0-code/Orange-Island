# Foire Aux Questions

## API & Fournisseurs

### Comment obtenir une clé API ?

- **Google Gemini** : [Google AI Studio](https://aistudio.google.com/apikey) — offre gratuite disponible
- **OpenAI** : [Platform API Keys](https://platform.openai.com/api-keys)
- **Anthropic** : [Console API Keys](https://console.anthropic.com/)
- **DeepSeek** : [Platform](https://platform.deepseek.com/)
- **OpenRouter** : [Keys page](https://openrouter.ai/keys)
- **Brave Search** : [Brave Search API](https://api.search.brave.com/)

### Puis-je utiliser plusieurs clés API pour le même fournisseur ?

Oui. Chaque fournisseur prend en charge plusieurs clés nommées. Appuyez sur le bouton radio pour sélectionner la clé active. Utile pour alterner entre des clés professionnelles/personnelles ou avoir une sauvegarde prête. Voir [Fournisseurs API](provider.md#api-keys).

### Comment ajouter un fournisseur personnalisé ?

Allez dans Paramètres → Fournisseur → **+ Ajouter un Fournisseur Personnalisé**. Saisissez un nom et une URL de base. Tout point de terminaison compatible OpenAI fonctionne. Voir [Fournisseurs Personnalisés](provider.md#custom-providers).

---

## Modèles Locaux

### Quels modèles GGUF fonctionnent ?

橘子岛 prend en charge le format GGUF pour le chat et l'embedding. Les modèles de chat doivent tenir dans la mémoire de l'appareil (1–8B paramètres selon la RAM). Les modèles d'embedding sont beaucoup plus petits (100–500 Mo). Voir [Modèles Locaux](local-model.md).

### Comment exécuter des modèles hors ligne ?

Importez un modèle de chat GGUF via Paramètres → Fournisseur → Local → **Importer un Modèle GGUF**. Pour une recherche sémantique entièrement hors ligne, importez également un modèle d'embedding GGUF. Aucune connexion réseau nécessaire.

### Pourquoi mon modèle local est-il si lent ?

L'inférence locale s'exécute sur le CPU de votre appareil. Elle est intrinsèquement plus lente que les API cloud. Conseils : utilisez des modèles plus petits (1–3B paramètres), une quantification plus basse (Q4_K_M), des fenêtres de contexte plus courtes et fermez les applications en arrière-plan.

---

## Embeddings & Recherche

### Pourquoi le test de mon modèle d'embedding échoue-t-il ?

Causes courantes :

- **Nom de modèle incorrect** — vérifiez l'orthographe exacte, y compris les tags Ollama (par ex., `qwen3-embedding:8b` pas `qwen3-embedding`)
- **URL de base incorrecte** — assurez-vous que le point de terminaison prend en charge `/v1/embeddings`
- **Clé API manquante** — certains fournisseurs exigent une authentification même pour les embeddings
- **Réseau** — vérifiez la connectivité vers le point de terminaison

### Quelle est la différence entre recherche par mot-clé et recherche RAG ?

La recherche par mot-clé correspond au texte exact. La recherche RAG (sémantique) correspond par sens — "configuration de base de données" peut trouver "configuration Room" même sans mots partagés. RAG nécessite un modèle d'embedding et des messages mis en cache. Voir [Recherche de Conversations](search.md).

### Comment utiliser Ollama pour les embeddings ?

1. Installez Ollama sur une machine
2. Téléchargez un modèle d'embedding : `ollama pull qwen3-embedding:8b`
3. Dans 橘子岛, ajoutez un modèle d'embedding distant avec le préréglage **Ollama**
4. Utilisez `http://<hôte>:11434/v1` comme URL de base
5. Saisissez le nom exact du modèle, tag inclus (par ex., `qwen3-embedding:8b`)
6. Laissez la clé API vide

---

## Mémoire

### Quelle est la différence entre Mémoire Active et Mémoires Sauvegardées ?

**La Mémoire Active** est un contexte persistant unique inclus dans **chaque appel API** — le modèle la voit toujours. **Les Mémoires Sauvegardées** sont une collection de fichiers nommés que le modèle recherche et récupère à la demande. Utilisez la Mémoire Active pour des faits persistants ; utilisez les Mémoires Sauvegardées pour du matériel de référence. Voir [Mémoire & Cache](memory.md).

### Le modèle peut-il modifier mes mémoires ?

Oui, si vous activez **Accéder aux Mémoires Sauvegardées** et/ou **Accéder à la Mémoire Active** dans Paramètres → Mémoire. Le modèle peut créer, lire, modifier et supprimer des mémoires via des appels d'outils. Toutes les autorisations sont désactivées par défaut.

---

## Shell & Outils

### Comment configurer l'accès shell distant ?

Déployez le serveur [Conch](https://github.com/orangeisland/conch) sur votre machine cible, puis ajoutez l'appareil dans Paramètres → Shell avec son URL et sa clé API. Voir [Shell Distant](shell.md).

### Puis-je rechercher sur le web sans clé API ?

Oui. **DuckDuckGo Lite** est le fournisseur de recherche web par défaut et ne nécessite aucune clé API. Il fonctionne immédiatement — activez simplement Recherche Web dans Paramètres → Recherche Web. Pour une meilleure fiabilité, configurez l'un des fournisseurs basés sur API (Brave, Serper, Tavily, SearXNG). Voir [Recherche Web](web-search.md).

### La connexion shell est-elle chiffrée ?

Oui. Conch utilise l'échange de clés ECDH + chiffrement AES-256-GCM + signature HMAC-SHA256. Tout le trafic entre 橘子岛 et le serveur Conch est chiffré de bout en bout.

---

## Données

### Comment sauvegarder mes données ?

Allez dans Paramètres → Contrôle des Données → **Exporter les Données** pour créer une sauvegarde manuelle dans un fichier `.橘子岛`. Pour une protection sans intervention, activez **Sauvegarde Automatique** dans Paramètres → Contrôle des Données → Sauvegarde Automatique — elle sauvegarde périodiquement vos données en arrière-plan. Voir [Portabilité des Données](import-export.md).

### Puis-je importer depuis ChatGPT ou Claude ?

Oui. Exportez vos données depuis ChatGPT ou Claude (ils fournissent des fichiers `.zip`), puis importez dans Paramètres → Contrôle des Données → **Tiers**. Les stratégies Fusion et Remplacement sont prises en charge. Voir [Portabilité des Données](import-export.md#third-party-import).

### Mes clés API sont-elles incluses dans les exports ?

Elles peuvent l'être, mais c'est optionnel. L'écran d'export vous permet d'activer l'inclusion des clés API. Un avertissement s'affiche lorsque vous l'activez. Les clés sont stockées en texte brut dans le fichier `.橘子岛`, donc ne les incluez que pour des migrations complètes d'appareil vers des destinations de confiance.

---

## Général

### Où sont stockées mes données ?

Tout est stocké localement sur votre appareil Android dans une base de données Room. 橘子岛 n'a pas de serveurs, pas de synchronisation cloud, pas de télémétrie. Les messages sont envoyés directement de votre appareil au fournisseur d'IA que vous configurez.

### 橘子岛 prend-il en charge plusieurs langues ?

Oui. L'interface de l'application prend en charge **l'anglais**, **le chinois (中文)** et **le chinois traditionnel (繁體中文)**. Paramètres → Langue. Un redémarrage est nécessaire après le changement.

### Comment signaler un bug ou demander une fonctionnalité ?

Ouvrez une issue sur [GitHub](https://github.com/chloemeadow0-code/Orange-Island/issues). Pour les contributions, consultez la section [Contributing](https://github.com/chloemeadow0-code/Orange-Island#contributing) du README.
