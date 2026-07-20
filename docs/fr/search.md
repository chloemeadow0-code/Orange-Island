# Recherche de Conversations

橘子岛 peut rechercher dans tout votre historique de conversations — soit par correspondance de mots-clés, soit par recherche sémantique (basée sur le sens) en utilisant des modèles d'embedding.

## Méthodes de Recherche

### Recherche par Mot-Clé

Correspondance de texte rapide et exacte. La recherche cherche les occurrences littérales de votre requête dans le contenu des messages.

**Idéal pour :**
- Trouver une phrase ou un terme spécifique
- Des recherches rapides quand vous vous souvenez du libellé exact
- Zéro configuration — fonctionne immédiatement

**Limitations :**
- Ne trouve pas les synonymes et les concepts liés
- Pas de compréhension du sens

### Recherche Sémantique (RAG)

Utilise des modèles d'embedding pour trouver des messages par **sens**, pas par mots exacts. Une requête comme "comment configurer la base de données" peut trouver des messages sur "configuration Room" même si le mot "base de données" n'apparaît jamais.

**Idéal pour :**
- Trouver des conversations par sujet ou thème
- Des requêtes larges où vous ne vous souvenez pas du libellé exact
- Découvrir des discussions connexes dans différentes conversations

**Prérequis :**
- Un modèle d'embedding doit être configuré (voir [Embedding / RAG](embedding.md))
- Les messages doivent être mis en cache (embeddings générés)

---

## Configuration

### 1. Ajouter un Modèle d'Embedding

Voir [Embedding / RAG](embedding.md) pour la configuration détaillée. Vous pouvez utiliser :
- **Modèles distants** (OpenAI, Mistral, Voyage, Ollama, etc.)
- **Modèles locaux** (fichiers GGUF, entièrement hors ligne)

### 2. Choisir les Méthodes de Recherche

Dans **Paramètres → Recherche de Conversations** :

| Paramètre | Description |
|-----------|-------------|
| **Méthode de Recherche du Modèle** | Comment le modèle recherche lorsqu'il appelle l'outil `search_conversations` |
| **Méthode de Recherche Manuelle** | Comment la barre de recherche dans le tiroir des conversations fonctionne |

Définissez chacun sur **Mot-Clé** ou **Sémantique (RAG)**.

### 3. Configurer la Portée de Recherche

| Paramètre | Plage | Description |
|-----------|-------|-------------|
| **Messages de contexte par résultat** | 4–32 | Combien de messages environnants inclure avec chaque correspondance (pas : 4, 8, 12, 16, 20, 24, 28, 32) |
| **Résultats de recherche max** | 5–30 | Nombre maximum de correspondances à retourner (pas : 5, 10, 15, 20, 25, 30) |
| **Seuil de Similarité** | 0,0–1,0 | RAG uniquement : score de similarité minimum pour une correspondance. Plus élevé = plus strict. Par défaut : 0,5 |

### 4. Mettre en Cache les Messages

Si vous utilisez RAG, appuyez sur **Mettre en Cache** pour générer des embeddings pour tous les messages existants. Activez **Mise en cache automatique** pour maintenir l'index à jour automatiquement.

---

## Utiliser la Recherche

### Recherche Manuelle (Barre de Recherche)

1. Ouvrez le **tiroir des conversations** (menu hamburger :material-menu: ou balayez vers la droite)
2. Appuyez sur la barre de recherche en haut
3. Tapez votre requête
4. Les résultats apparaissent ci-dessous — appuyez sur n'importe quel résultat pour ouvrir cette conversation au message correspondant

### Recherche Initiée par le Modèle

Lorsque **Accéder aux Conversations Passées** est activé (Paramètres → Mémoire), le modèle peut rechercher dans votre historique de manière autonome :

```text
Vous : "Qu'avons-nous décidé à propos de la conception de l'API la semaine dernière ?"
Modèle : [Recherche "décision conception API"]
         "Mardi dernier, nous avons décidé d'utiliser..."
```

La recherche apparaît comme une carte d'outil dans la conversation.

---

## Seuil de Similarité

Le curseur **Seuil de Similarité** (0,0 à 1,0) contrôle à quel point un message doit correspondre pour être inclus dans les résultats RAG :

- **Bas (0,3–0,5)** : Plus de résultats, peut inclure du contenu vaguement lié
- **Moyen (0,5–0,7)** : Équilibré — bon par défaut
- **Élevé (0,7–0,9)** : Moins de résultats, seulement des correspondances très proches

Commencez avec la valeur par défaut et ajustez selon vos résultats. Si vous obtenez trop de correspondances non pertinentes, augmentez le seuil. Si vous manquez des conversations pertinentes, baissez-le.

---

## Affichage des Résultats de Recherche

Dans le tiroir des conversations, les résultats de recherche montrent :

- **Titre de la conversation** (ou "Sans titre")
- **Message correspondant** — le message utilisateur ou modèle qui a correspondu
- **Étiquette de rôle** — Utilisateur ou Modèle
- **Messages de contexte** — messages environnants pour le contexte

Appuyez sur un résultat pour ouvrir la conversation défilée jusqu'au message correspondant.
