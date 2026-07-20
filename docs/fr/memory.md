# Mémoire & Cache

橘子岛 dispose d'un système de mémoire persistante qui permet au modèle de se souvenir d'informations à travers les conversations. Combiné à la mise en cache automatique basée sur les embeddings, il fournit une base de connaissances qui grandit avec votre utilisation.

## Types de Mémoire

### Mémoire Active

Un contexte de mémoire unique et toujours actif qui est inclus dans **chaque appel API** au modèle. Pensez-y comme une note autocollante que le modèle voit toujours.

**Utilisez la Mémoire Active pour :**
- Votre nom, préférences et contexte personnel
- Le contexte de projet que le modèle doit toujours connaître
- Des instructions permanentes qui s'appliquent à toutes les conversations
- Des faits que vous en avez assez de répéter

**Exemple de contenu de Mémoire Active :**
```text
Utilisateur : Newo Ether
Préférences : Préfère le chinois pour le chat informel, l'anglais pour les sujets techniques.
Projet : Construction d'橘子岛 — un client LLM Android BYOK.
Style de codage : Kotlin, Jetpack Compose, architecture MVVM.
```

#### Modifier la Mémoire Active

1. Allez dans **Paramètres → Mémoire**
2. Faites défiler jusqu'à **Mémoire Active**
3. Appuyez sur **Modifier la Mémoire Active**
4. Saisissez votre contenu
5. Appuyez sur **Enregistrer**

Le modèle peut également mettre à jour la mémoire active via des appels d'outils si **Accéder à la Mémoire Active** est activé.

---

### Mémoires Sauvegardées

Une collection de fichiers de mémoire nommés que le modèle peut rechercher, lire, créer, modifier et supprimer. Contrairement à la Mémoire Active (toujours envoyée), les mémoires sauvegardées sont récupérées à la demande.

**Utilisez les Mémoires Sauvegardées pour :**
- Du matériel de référence (docs API, détails de configuration, commandes)
- Des notes spécifiques au projet
- Des apprentissages et perspectives de conversations passées
- Tout ce que vous voulez que le modèle se rappelle quand c'est pertinent

#### Créer des Mémoires Manuellement

1. Allez dans **Paramètres → Mémoire**
2. Appuyez sur **Ajouter une Mémoire**
3. Saisissez :
    - **Titre** — nom descriptif
    - **Description** — bref résumé (utilisé pour la correspondance de recherche)
    - **Contenu** — le contenu complet de la mémoire
4. Appuyez sur **Créer**

#### Mémoires Créées par le Modèle

Lorsque **Accéder aux Mémoires Sauvegardées** est activé, le modèle peut créer, lire, mettre à jour et supprimer des fichiers de mémoire via des appels d'outils. Cela permet au modèle de :

- Se souvenir des faits que vous lui dites
- Sauvegarder des extraits de code ou des configurations utiles
- Construire une base de connaissances au fil du temps
- Nettoyer les informations obsolètes

---

## Autorisations de Mémoire

Contrôlez ce à quoi le modèle peut accéder :

| Paramètre | Emplacement | Quand Activer |
|-----------|-------------|---------------|
| **Accéder aux Mémoires Sauvegardées** | Paramètres → Mémoire | Vous voulez que le modèle lise/écrive des fichiers de mémoire |
| **Accéder à la Mémoire Active** | Paramètres → Mémoire | Vous voulez que le modèle mette à jour le contexte persistant |
| **Accéder aux Conversations Passées** | Paramètres → Recherche de Conversations | Vous voulez que le modèle recherche dans l'historique de chat |

Les trois sont **désactivés** par défaut. Activez seulement ce dont vous avez besoin.

---

## Mise en Cache Automatique

La mise en cache automatique génère automatiquement des embeddings pour les nouveaux messages dès leur arrivée. Cela maintient votre index de recherche de conversations à jour sans intervention manuelle.

### Activer la Mise en Cache Automatique

1. Allez dans **Paramètres → Recherche de Conversations**
2. Choisissez un modèle d'embedding (si ce n'est pas déjà fait — voir [Embedding / RAG](embedding.md))
3. Sous **Mise en Cache**, activez **Mise en cache automatique des nouveaux messages**

Lorsqu'elle est activée, chaque nouveau message (utilisateur et modèle) est automatiquement intégré et indexé pour la recherche sémantique.

### Mise en Cache Manuelle

Si la mise en cache automatique est désactivée, vous pouvez mettre en cache manuellement les messages :

1. Allez dans **Paramètres → Recherche de Conversations**
2. Appuyez sur **Mettre en Cache** — calcule les embeddings pour tous les messages non mis en cache
3. La progression est affichée sous forme d'indicateur circulaire

Appuyez sur **Re-mettre en Cache** pour reconstruire tout l'index à partir de zéro. Cela supprime tous les embeddings mis en cache et retraite chaque message. À utiliser lorsque :
- Vous avez changé de modèle d'embedding
- Le cache semble corrompu ou obsolète
- Les résultats de recherche sont étonnamment mauvais

!!! warning
    La re-mise en cache est irréversible et peut prendre du temps selon votre nombre de messages et la vitesse du modèle d'embedding.

### État du Cache

Les paramètres du modèle d'embedding montrent combien de messages sont en cache vs non mis en cache :
- **"Tous les N messages en cache"** — à jour
- **"X sur Y messages non mis en cache"** — retard à traiter

---

## Appels d'Outils de Mémoire dans le Chat

Lorsque le modèle utilise les outils de mémoire, vous verrez des cartes intégrées :

| Outil | Texte de la Carte |
|-------|-------------------|
| Rechercher | "A parcouru N mémoires sauvegardées" |
| Lire | "A lu [nom de la mémoire]" |
| Sauvegarder | "A sauvegardé [nom de la mémoire]" |
| Modifier | "A mis à jour [nom de la mémoire]" |
| Supprimer | "A supprimé [nom de la mémoire]" |
| Mettre à jour Active | "A mis à jour la mémoire active" |

Appuyez sur n'importe quelle carte pour voir le contenu complet qui a été lu ou écrit.

---

## Bonnes Pratiques

- **Gardez la Mémoire Active concise** — elle est incluse dans chaque appel API, donc un contenu verbeux gaspille des tokens
- **Utilisez des titres descriptifs pour les Mémoires Sauvegardées** — les titres aident le modèle à trouver la bonne mémoire
- **Activez la mise en cache automatique** si vous utilisez régulièrement la recherche de conversations
- **Re-metrez en cache après avoir changé de modèle d'embedding** — différents modèles produisent des embeddings incompatibles
