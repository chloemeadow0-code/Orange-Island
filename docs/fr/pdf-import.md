# Import PDF

橘子岛 peut extraire et envoyer des pages sélectionnées de fichiers PDF sous forme d'images aux modèles compatibles vision.

## Comment Ça Marche

1. Joignez un fichier PDF dans le chat
2. Une boîte de dialogue s'ouvre affichant toutes les pages sous forme de vignettes
3. Sélectionnez les pages à envoyer
4. Confirmez — 橘子岛 extrait les pages sous forme d'images et les envoie au modèle

Le modèle reçoit les pages comme entrée visuelle, lui permettant de lire et d'analyser le contenu PDF.

---

## Sélection de Pages

### Choisir les Pages

- Chaque vignette de page a une **case à cocher** dans le coin supérieur gauche
- Appuyez sur la case à cocher pour activer/désactiver une page
- Boutons **Tout Sélectionner** / **Tout Désélectionner** pour les opérations groupées
- La barre supérieure affiche le nombre sélectionné (par ex., "3 sélectionnées")
- Maximum **50 pages** par PDF (les fichiers plus grands sont tronqués)

### Aperçu Plein Écran

Appuyez sur n'importe quelle vignette pour ouvrir la visionneuse plein écran :

| Geste | Action |
|-------|--------|
| Balayer gauche/droite | Naviguer entre les pages |
| Appuyer | Afficher/masquer les contrôles superposés |
| Pincer | Zoom avant/arrière |
| Pastille en bas à gauche | Basculer la sélection de page |

L'aperçu vous permet d'inspecter les pages avant de décider lesquelles envoyer.

---

## Envoyer des Pages

Après avoir sélectionné les pages, appuyez sur le bouton de confirmation. 橘子岛 :

1. Rend chaque page PDF sélectionnée comme une image haute résolution
2. Joint les images à votre message
3. Les envoie au modèle (nécessite un modèle compatible vision)

---

## Limitations

- **Max 50 pages** par PDF — les fichiers plus grands sont tronqués
- **Image uniquement** : Le texte n'est pas extrait ; le modèle lit les pages visuellement
- **Modèle de vision requis** : Le modèle de chat actif doit prendre en charge les entrées d'image
- **Taille de fichier** : Bien qu'il n'y ait pas de limite stricte de taille de PDF, les très gros fichiers peuvent être lents à rendre

---

## Cas d'Utilisation

- **Analyse de documents** — téléversez un contrat, rapport ou article pour que le modèle l'examine
- **Recherche** — partagez des pages spécifiques d'articles académiques
- **Traduction** — envoyez des documents en langue étrangère pour traduction
- **Résumé** — obtenez des résumés de longs documents, page par page
