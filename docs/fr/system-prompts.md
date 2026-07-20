# Prompts Système

Les prompts système définissent le personnage, le comportement et les règles de base du modèle. 橘子岛 vous donne un contrôle fin sur la façon dont les instructions sont assemblées et envoyées au modèle.

## Éditeur à Trois Sections

Chaque modèle de prompt système a trois sections modifiables indépendamment :

```text
┌─────────────────────────────────┐
│ Prompt Système                  │ ← Instructions principales (personnage, règles, ton)
├─────────────────────────────────┤
│ Préfixe Utilisateur             │ ← Ajouté avant chaque message utilisateur
├─────────────────────────────────┤
│ Suffixe Utilisateur             │ ← Ajouté après chaque message utilisateur
└─────────────────────────────────┘
```

### Prompt Système

Le bloc d'instructions principal. C'est ici que vous définissez :

- **Personnage** : "Vous êtes un développeur Python senior axé sur l'architecture propre."
- **Règles** : "Répondez toujours en chinois. Utilisez des puces pour les listes."
- **Contraintes** : "Ne vous excusez jamais. Soyez concis. Préférez le code à l'explication."

### Préfixe & Suffixe Utilisateur

Ceux-ci enveloppent chaque message que vous envoyez :

- **Préfixe Utilisateur** — ajouté avant le texte de votre message. Utile pour des rappels ou des balises de contexte.
- **Suffixe Utilisateur** — ajouté après le texte de votre message. Utile pour des instructions de clôture.

**Exemple** : Si votre préfixe est `[Contexte : travail sur la documentation 橘子岛]` et le suffixe est `\n\nVeuillez répondre en Markdown.`, le modèle reçoit :

```text
[Contexte : travail sur la documentation 橘子岛]
Comment configurer la recherche web ?
Veuillez répondre en Markdown.
```

---

## Créer un Prompt

1. Allez dans **Paramètres → Prompts Système**
2. Appuyez sur **Ajouter un Nouveau Prompt**
3. Saisissez un **titre** (par ex., "Traducteur", "Réviseur de Code", "Assistant Chinois")
4. Remplissez les trois sections :
    - Appuyez sur **Ajouter du Texte** pour écrire du contenu statique
    - Appuyez sur **Ajouter une Variable** pour insérer des valeurs dynamiques
5. Appuyez sur **Enregistrer**

### Réorganiser les Éléments

Dans chaque section, vous pouvez avoir plusieurs blocs de texte et variables. Appui long sur un élément pour :

- **Monter** / **Descendre** — réorganiser dans la section
- **Supprimer** — supprimer l'élément

---

## Substitution de Variables

Les variables sont remplacées par des valeurs dynamiques lorsque le message est envoyé :

| Variable | Se Développe en | Exemple | Quand Résolu |
|----------|-----------------|---------|--------------|
| `{time}` | Heure actuelle (HH:mm:ss) | `14:30:00` | Compilation du prompt |
| `{date}` | Date actuelle (YYYY-MM-DD) | `2026-05-10` | Compilation du prompt |
| `{sent_time}` | Heure d'envoi du message (HH:mm) | `10:05` | Par message |
| `{sent_date}` | Date d'envoi du message (YYYY-MM-DD) | `2026-05-11` | Par message |
| `{active_memory}` | Contenu de la mémoire active | `[Votre contenu de mémoire sauvegardé]` | Compilation du prompt |
| `{model_id}` | ID du modèle actuellement sélectionné | `gemini-1.5-flash` | Compilation du prompt |

**Les variables par message** (`{sent_time}`, `{sent_date}`) sont résolues chaque fois que vous envoyez un message, donc elles reflètent l'heure d'envoi exacte. **Les variables au niveau du prompt** (`{time}`, `{date}`, `{active_memory}`, `{model_id}`) sont résolues lorsque le prompt système est compilé.

!!! tip
    Utilisez `{sent_date}` pour les prompts sensibles à la date comme "Aujourd'hui nous sommes le {sent_date}. Lorsque vous discutez d'événements récents, notez que vos connaissances peuvent être obsolètes." Utilisez `{active_memory}` pour injecter la mémoire persistante du modèle dans les instructions système.

### Ajouter une Variable

1. Dans n'importe quelle section de l'éditeur, appuyez sur **Ajouter une Variable**
2. Sélectionnez la variable dans le sélecteur
3. Elle apparaît comme une pastille/puce dans la section — faites glisser pour repositionner

---

## Gérer les Prompts

### Définir comme Par Défaut

Appuyez sur le bouton radio à côté d'un prompt pour en faire le **prompt global par défaut**. Toutes les conversations utilisent ce prompt sauf remplacement.

### Remplacement par Conversation

Chaque conversation peut utiliser un prompt système différent :

1. Ouvrez une conversation
2. Appuyez sur le menu contextuel (:material-dots-vertical:) dans la barre supérieure
3. Sélectionnez **Prompt de Conversation**
4. Choisissez un prompt dans la liste

Le paramètre par conversation remplace le paramètre global par défaut pour cette conversation uniquement.

### Modifier ou Supprimer

- Appuyez sur un prompt pour le **modifier**
- Appui long et sélectionnez **Supprimer** pour le retirer

!!! warning
    La suppression d'un prompt système est permanente. Les conversations qui l'utilisaient reviendront au prompt global par défaut.

---

## Aucun Prompt Système

Si aucun prompt système n'est sélectionné, le modèle ne reçoit aucune instruction spéciale — il se comporte selon son entraînement de base. C'est parfois souhaitable pour les tests ou pour les modèles qui fonctionnent mieux sans instructions système.

Pour n'utiliser aucun prompt, sélectionnez **Aucun** dans la liste des prompts.

---

## Génération Automatique de Titres

橘子岛 peut générer automatiquement des titres de conversation après la première réponse :

1. Allez dans **Paramètres → Génération de Titres**
2. Activez **Génération Automatique de Titres**
3. Choisissez un **Modèle de Titre** :
    - **Utiliser le Modèle Actuel** — utilise le modèle actif dans la conversation
    - **Sélectionner un Modèle de Titre** — choisissez un modèle rapide/économique spécifique pour la génération de titres

Lorsqu'elle est activée, une brève barre de notification "Génération du titre..." apparaît après la première réponse du modèle, et la conversation est automatiquement renommée de "Sans titre" à un titre descriptif.

---

## Exemples de Prompts

### Traducteur

```yaml
Prompt Système : |
  Vous êtes un traducteur professionnel. Traduisez les entrées utilisateur en anglais.
  Préservez la mise en forme, les blocs de code et les termes techniques. N'ajoutez pas d'explications.
```

### Réviseur de Code

```yaml
Prompt Système : |
  Vous êtes un réviseur de code senior. Lorsqu'on vous montre du code :
  1. Identifiez les bugs et les cas limites
  2. Suggérez des améliorations de performance
  3. Vérifiez les problèmes de sécurité
  Soyez spécifique. Référencez les numéros de ligne quand c'est possible.
```

### Assistant Chinois

```yaml
Prompt Système : |
  你是一个乐于助人的中文助手。用简洁、清晰的中文回答问题。
Suffixe Utilisateur : |
  \n\n请用中文回答。
```
