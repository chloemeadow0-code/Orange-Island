# Conversations

Le système de conversation d'橘子岛 est construit autour du **branchement non linéaire** — contrairement à la plupart des applications de chat, vous pouvez modifier n'importe quel message passé et explorer des chemins de réponse alternatifs sans perdre la conversation originale.

## Créer des Conversations

Appuyez sur **Nouveau Chat** dans le tiroir des conversations, ou commencez simplement à taper dans l'écran de chat. Une nouvelle conversation est créée automatiquement avec votre premier message.

Les conversations reçoivent un titre automatique après la première réponse (si la [Génération de Titres](system-prompts.md#auto-title-generation) est activée), ou vous pouvez les renommer manuellement.

## Gérer les Conversations

### Changer de Conversation

Ouvrez le **tiroir des conversations** (menu hamburger :material-menu: ou balayez vers la droite) et appuyez sur n'importe quelle conversation pour l'ouvrir.

### Renommer

1. Appui long sur une conversation dans le tiroir
2. Appuyez sur **Renommer**
3. Saisissez un nouveau titre et enregistrez

### Supprimer

1. Appui long sur une conversation dans le tiroir
2. Appuyez sur **Supprimer**
3. Confirmez la suppression — cette action ne peut pas être annulée

---

## Branchement Non Linéaire

C'est la fonctionnalité signature d'橘子岛. Chaque message peut être un point de branchement.

### Modifier un Message Passé

1. Appui long sur n'importe quelle bulle de message (utilisateur ou modèle)
2. Appuyez sur **Modifier**
3. Modifiez le contenu du message
4. Envoyez — 橘子岛 crée une **nouvelle branche** à partir de ce point

La branche originale est préservée. Vous pouvez basculer entre les branches à tout moment.

### Comment Fonctionnent les Branches

Chaque message vit dans une **structure arborescente** :

```text
Message 1 (Utilisateur)
├── Message 2 (Modèle) ← réponse originale
└── Message 3 (Modèle) ← branche créée après modification du Message 1
    ├── Message 4 (Utilisateur)
    └── ...
```

Lorsque vous modifiez un message et régénérez, la nouvelle réponse devient un frère de l'original — les deux existent sous le même message parent.

### Basculer entre les Branches

Lorsqu'un message a plusieurs enfants (branches), l'interface affiche des contrôles de navigation pour basculer entre eux. Vous pouvez explorer des chemins alternatifs sans perdre le contexte.

### Pourquoi Brancher ?

- **Explorer des alternatives** — posez la même question avec une formulation différente
- **Tester A/B des prompts** — comparez les réponses de différents prompts système ou modèles
- **Corriger des erreurs** — corrigez une faute de frappe dans votre question sans perdre le fil original
- **Itérer** — affinez un prompt à travers plusieurs versions tout en conservant toutes les tentatives

---

## Opérations sur les Messages

Appui long sur n'importe quel message pour accéder à ces actions :

| Action | Description |
|--------|-------------|
| **Copier** | Copier le texte du message dans le presse-papiers |
| **Modifier** | Modifier le message et créer une branche |
| **Infos** | Voir les métadonnées : horodatage, modèle utilisé, nombre de tokens |
| **Supprimer** | Supprimer ce message et toutes les réponses suivantes |

!!! warning "Supprimer un Message"
    Supprimer un message supprime également toutes les réponses qui le suivent. Cela ne peut pas être annulé.

---

## La Barre Inférieure

La zone de saisie de chat fournit un accès rapide aux contrôles essentiels :

### Sélecteur de Modèle

Appuyez sur le nom du modèle à gauche de la barre inférieure pour ouvrir le **sélecteur de modèle**. Vous pouvez changer de modèle à tout moment — même en milieu de conversation. Différents messages dans la même conversation peuvent utiliser différents modèles.

### Pièces Jointes

Appuyez sur **+** (:material-plus:) pour joindre des fichiers :

- **Photos** — images de votre galerie
- **Vidéos** — fichiers vidéo (avec prise en charge de l'extraction d'images)
- **Fichiers** — tout type de fichier, y compris les PDF

Les formats d'image pris en charge sont envoyés directement aux modèles compatibles vision. Les fichiers PDF ouvrent une boîte de dialogue de sélection de pages.

### Envoi

Tapez votre message et appuyez sur **Envoyer** (:material-send:). Le modèle diffuse sa réponse token par token.

---

## Streaming & Affichage

### Streaming en Temps Réel

Les réponses apparaissent mot par mot au fur et à mesure que le modèle les génère. 橘子岛 fait défiler automatiquement pour garder le contenu le plus récent visible. Appuyez sur le **bouton défiler vers le bas** (apparaît lorsque vous faites défiler vers le haut) pour revenir à la réponse en direct.

### Rendu Markdown

Les réponses du modèle sont rendues avec un support markdown complet :

- **Titres**, **gras**, *italique*, `code en ligne`
- **Blocs de code** avec coloration syntaxique (utilisez ````` ``` `````)
- **Tableaux**, citations, listes
- **Mathématiques LaTeX** — en ligne `$E=mc^2$` et bloc `$$\int_a^b f(x)dx$$`

### Affichage de la Réflexion

Pour les modèles qui prennent en charge le raisonnement (série o d'OpenAI, réflexion étendue d'Anthropic, réflexion Gemini, DeepSeek-R1), le processus de pensée du modèle est affiché dans un **panneau repliable** avant la réponse finale :

- Le panneau montre "Réflexion en cours..." pendant la phase de raisonnement
- Une fois terminé, il affiche la durée de réflexion (par ex., "A réfléchi pendant 12s")
- Appuyez pour déplier/replier le contenu de la réflexion
- Les appels d'outils effectués pendant la réflexion sont comptés (par ex., "A réfléchi pendant 8s, a appelé 2 outils")

---

## Paramètres par Conversation

Chaque conversation peut remplacer les paramètres globaux par défaut :

- **Modèle** — sélectionnez un modèle différent pour cette conversation
- **Prompt Système** — utilisez une instruction système différente
- **Paramètres de génération** — température, tokens max, niveau de réflexion

Ces remplacements sont définis depuis le menu contextuel de la conversation dans la barre supérieure.

---

## Fenêtre de Contexte

橘子岛 suit l'utilisation des tokens en temps réel. Lorsqu'une conversation dépasse la fenêtre de contexte du modèle, les messages plus anciens sont visuellement **grisés** pour indiquer qu'ils sont en dehors du contexte actif. Le modèle ne "voit" plus les messages grisés, mais ils restent visibles dans votre interface.

Ajustez la taille de la fenêtre de contexte dans **Paramètres → Génération → Fenêtre de Contexte**.
