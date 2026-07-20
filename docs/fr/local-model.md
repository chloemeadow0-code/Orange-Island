# Modèles Locaux

Exécutez des LLM directement sur votre appareil Android en utilisant des fichiers de modèle GGUF et llama.cpp. Aucun réseau requis, pas de clés API, entièrement privé.

## Comment Ça Marche

橘子岛 intègre llama.cpp via Android NDK (CMake). Lorsque vous importez un fichier GGUF, le modèle s'exécute entièrement sur le CPU de votre appareil — aucune donnée ne quitte l'appareil.

## Prérequis

- **Format GGUF** uniquement (le standard pour llama.cpp)
- **Mémoire de l'appareil** : Le modèle doit tenir dans la RAM disponible. En règle générale :
    - Modèles 1–3B paramètres : 4–6 Go de RAM
    - Modèles 7–8B paramètres : 6–8 Go de RAM
- **Stockage** : Les fichiers GGUF vont d'environ 500 Mo (petits modèles quantifiés) à plus de 5 Go

!!! warning
    L'inférence locale est intensive en CPU et beaucoup plus lente que les API cloud. C'est idéal pour une utilisation hors ligne, le contenu sensible à la confidentialité ou l'expérimentation — pas pour du chat rapide à haut volume.

---

## Importer un Modèle de Chat

1. Téléchargez un fichier de modèle GGUF sur votre appareil (voir les sources recommandées ci-dessous)
2. Allez dans **Paramètres → Fournisseur**
3. Sélectionnez **Local** comme fournisseur
4. Appuyez sur **Importer un Modèle GGUF**
5. Sélectionnez le fichier `.gguf` depuis votre appareil
6. Configurez le modèle :

| Paramètre | Description | Exemple |
|-----------|-------------|---------|
| **ID du Modèle** | Identifiant en minuscules, sans espaces | `qwen3-8b` |
| **Alias** | Nom d'affichage | `Qwen 3 8B` |
| **Taille du Contexte** | Fenêtre de contexte maximale en tokens | `4096` |
| **Température** | Aléatoire (0,0–2,0) | `0,7` |
| **Top P** | Seuil d'échantillonnage par noyau (0,0–1,0) | `0,9` |
| **Tokens Max** | Longueur de génération maximale | `2048` |

7. Appuyez sur **Ajouter**

Le modèle est importé et prêt à être utilisé immédiatement.

---

## Importer un Modèle d'Embedding

Les modèles d'embedding sont plus petits et utilisés pour la recherche sémantique :

1. Allez dans **Paramètres → Recherche de Conversations**
2. Appuyez sur **Ajouter un Modèle Local**
3. Sélectionnez un fichier de modèle d'embedding `.gguf`
4. Donnez-lui un nom
5. Appuyez sur **Ajouter**

Voir [Embedding / RAG](embedding.md) pour la configuration de la recherche.

---

## Sélectionner le Modèle Actif

Après avoir importé un ou plusieurs modèles :

1. Allez dans **Paramètres → Fournisseur → Local**
2. Vous verrez tous les modèles importés listés
3. Appuyez sur le **bouton radio** à côté du modèle que vous voulez utiliser
4. Le modèle sélectionné devient actif lorsque **Local** est choisi comme fournisseur de chat

---

## Gérer les Modèles Locaux

### Renommer

Appuyez sur un modèle pour changer son alias ou ajuster les paramètres (température, taille du contexte, etc.).

### Supprimer

Appui long sur un modèle et appuyez sur **Supprimer**. Cela retire le modèle d'橘子岛 et supprime le fichier GGUF du stockage.

---

## Modèles Recommandés

### Modèles de Chat

| Modèle | Taille | RAM Nécessaire | Notes |
|--------|--------|----------------|-------|
| Qwen 3 1.7B | ~1 Go | 3–4 Go | Bonne qualité pour sa taille |
| Llama 3.2 3B | ~2 Go | 4–5 Go | Solide polyvalent |
| Qwen 3 8B | ~5 Go | 7–8 Go | Meilleure qualité, RAM élevée |

### Modèles d'Embedding

| Modèle | Taille | Notes |
|--------|--------|-------|
| BGE Small EN v1.5 | ~130 Mo | Bons embeddings anglais |
| BGE Small ZH v1.5 | ~130 Mo | Optimisé pour le chinois |
| Nomic Embed Text v1.5 | ~270 Mo | Bon multilingue |

### Où Trouver des Fichiers GGUF

- [Hugging Face](https://huggingface.co/models?library=gguf) — recherchez "GGUF"
- [Modèles quantifiés de bartowski](https://huggingface.co/bartowski) — large sélection, bien organisée

!!! tip
    Cherchez la quantification `Q4_K_M` — elle offre le meilleur compromis entre qualité et taille pour les modèles de chat.

---

## Conseils de Performance

- **Contexte plus petit = plus rapide** : Commencez avec 2048 et augmentez seulement si nécessaire
- **Quantification plus basse = plus rapide** : Q4_K_M est plus rapide que Q6_K ou Q8
- **Fermez les autres applications** : L'inférence locale a besoin d'autant de RAM que possible
- **Branchez l'appareil** : L'inférence est intensive en CPU et une utilisation prolongée vide la batterie
