# Embedding / RAG

Les modèles d'embedding convertissent le texte en vecteurs numériques qui capturent le sens. 橘子岛 utilise ces vecteurs pour la recherche sémantique (RAG) dans votre historique de conversations — trouver des messages par ce qu'ils signifient, pas seulement par les mots qu'ils contiennent.

## Comment Ça Marche

1. Chaque message est envoyé à un modèle d'embedding
2. Le modèle retourne un vecteur (une liste de nombres) représentant le sens du message
3. Lorsque vous recherchez, votre requête est également intégrée
4. 橘子岛 calcule la **similarité cosinus** entre le vecteur de la requête et tous les vecteurs de messages
5. Les messages avec une similarité supérieure à votre seuil sont retournés comme correspondances

## Fournisseurs Pris en Charge

| Fournisseur | URL de Base | Clé API Requise | Notes |
|-------------|-------------|-----------------|-------|
| **OpenAI** | `https://api.openai.com/v1` | Oui | `text-embedding-3-small`, `text-embedding-3-large` |
| **Mistral** | `https://api.mistral.ai/v1` | Oui | `mistral-embed` |
| **Voyage AI** | `https://api.voyageai.com/v1` | Oui | `voyage-3`, `voyage-3-lite` |
| **SiliconFlow** | `https://api.siliconflow.cn/v1` | Oui | `BAAI/bge-large-zh-v1.5` (optimisé pour le chinois) |
| **Ollama** | `http://localhost:11434/v1` | Non | `qwen3-embedding`, `nomic-embed-text`, etc. |
| **Personnalisé** | Quelconque | Optionnel | Tout point de terminaison d'embeddings compatible OpenAI |
| **Local** | N/D | Non | Modèles d'embedding GGUF via llama.cpp |

---

## Ajouter un Modèle d'Embedding

### Distant (API)

1. Allez dans **Paramètres → Recherche de Conversations**
2. Appuyez sur **Ajouter un Modèle Distant**
3. Configurez :

| Champ | Description |
|-------|-------------|
| **Fournisseur** | Sélectionnez dans la liste déroulante (OpenAI, Mistral, Voyage, SiliconFlow, Ollama, Personnalisé) |
| **Nom du Modèle** | L'ID exact du modèle (par ex., `text-embedding-3-small`) |
| **URL de Base** | Pré-remplie pour les fournisseurs connus ; modifiable pour les proxys |
| **Clé API** | Laissez vide pour résoudre automatiquement depuis votre clé de fournisseur de chat, ou saisissez une clé dédiée |
| **Taille de Lot** | Messages à intégrer par requête API (1–100) |

4. Appuyez sur **Ajouter** — un test de connexion est exécuté avant l'enregistrement

!!! tip
    Le champ de clé API est optionnel si vous avez déjà configuré le même fournisseur pour le chat. Laissez-le vide et 橘子岛 résout votre clé API de chat automatiquement.

### Local (GGUF)

1. Allez dans **Paramètres → Recherche de Conversations**
2. Appuyez sur **Ajouter un Modèle Local**
3. Importez un fichier de modèle d'embedding `.gguf` (par ex., `bge-small-en-v1.5-q4_k.gguf`)
4. Donnez-lui un nom
5. Appuyez sur **Ajouter**

Les modèles d'embedding sont généralement beaucoup plus petits que les modèles de chat — quelques centaines de Mo au maximum.

### Ollama

1. Installez Ollama sur une machine
2. Téléchargez un modèle d'embedding : `ollama pull qwen3-embedding:8b`
3. Dans 橘子岛, ajoutez un modèle distant :
    - Fournisseur : **Ollama**
    - URL de Base : `http://<hôte>:11434/v1`
    - Nom du modèle : `qwen3-embedding:8b` (incluez le `:tag`)
    - Clé API : laissez vide
4. Appuyez sur **Ajouter**

!!! note
    Les suffixes de tag Ollama comme `:8b`, `:latest` font partie du nom du modèle. Utilisez le nom exact de `ollama list`.

---

## Mise en Cache

Après avoir ajouté un modèle, vous devez mettre en cache vos messages (générer des embeddings) :

1. Appuyez sur **Mettre en Cache** sur le modèle d'embedding
2. 橘子岛 traite tous les messages non mis en cache par lots
3. Un indicateur de progression circulaire montre la progression actuelle
4. Achèvement : "Tous les N messages en cache"

### Mise en Cache Automatique

Activez **Mise en cache automatique** pour intégrer automatiquement les nouveaux messages dès leur arrivée. Cela maintient votre index de recherche toujours à jour.

### Re-Mise en Cache

Appuyez sur **Re-mettre en Cache** pour supprimer tous les embeddings existants et reconstruire à partir de zéro. À utiliser lorsque :

- Vous passez à un modèle d'embedding différent
- La qualité des embeddings semble dégradée
- Le cache est incohérent

!!! warning
    La re-mise en cache ne peut pas être annulée et peut prendre beaucoup de temps pour les grands historiques de messages.

---

## Taille de Lot

Le paramètre **Taille de Lot** (1–100) contrôle combien de messages sont envoyés par requête API pendant la mise en cache :

- **Plus élevé** : Mise en cache plus rapide, mais charges utiles API plus grandes
- **Plus bas** : Requêtes plus petites, plus lent mais plus fiable sur les connexions lentes

Commencez avec la valeur par défaut et ajustez si vous rencontrez des délais d'expiration (baissez-la) ou voulez une mise en cache plus rapide (augmentez-la).

---

## Tester Votre Configuration

Lorsque vous ajoutez un modèle distant, 橘子岛 exécute un test de connexion automatique. S'il échoue :

1. Vérifiez le nom du modèle — incluez les tags pour Ollama (`:8b`, `:latest`)
2. Vérifiez que l'URL de base est accessible depuis votre appareil
3. Confirmez que la clé API est valide (si requise)
4. Essayez un nom de modèle connu pour ce fournisseur

Erreurs courantes :
- **"Nom de modèle incorrect"** — vérifiez l'orthographe exacte, tags inclus
- **"URL de base incorrecte"** — assurez-vous que le point de terminaison prend en charge `/v1/embeddings`
- **"Clé API manquante"** — certains fournisseurs exigent une authentification
- **"Erreur réseau"** — vérifiez la connectivité

---

## Recommandations de Fournisseurs

| Cas d'Utilisation | Fournisseur Recommandé |
|-------------------|------------------------|
| **Meilleure qualité (anglais)** | Voyage AI `voyage-3` |
| **Meilleure qualité (chinois)** | SiliconFlow `BAAI/bge-large-zh-v1.5` |
| **Gratuit / auto-hébergé** | Ollama `qwen3-embedding` ou `nomic-embed-text` |
| **Entièrement hors ligne** | GGUF Local `bge-small-en-v1.5` |
| **Déjà utilisateur OpenAI** | OpenAI `text-embedding-3-small` (économique, rapide) |
