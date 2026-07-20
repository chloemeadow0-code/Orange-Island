# Recherche Web

Permettez au modèle de rechercher sur internet et de récupérer des pages web en temps réel. Lorsqu'elle est activée, le modèle peut rechercher des informations actuelles, vérifier des faits, récupérer de la documentation ou rechercher des sujets — le tout de manière autonome via l'appel d'outils.

## Fournisseurs Pris en Charge

| Fournisseur | Description | Offre Gratuite | Configuration |
|-------------|-------------|----------------|---------------|
| **DuckDuckGo Lite** | Anonyme, aucune clé API nécessaire | Oui (illimité, meilleur effort) | Aucune configuration — fonctionne immédiatement |
| **Brave** | API de recherche axée sur la confidentialité | Oui (2 000 requêtes/mois) | [api.search.brave.com](https://api.search.brave.com/) |
| **Serper** | API de recherche Google rapide | Oui (2 500 requêtes/mois) | [serper.dev](https://serper.dev) |
| **Tavily** | Recherche optimisée pour l'IA, conçue pour les agents LLM | Oui (1 000 requêtes/mois) | [tavily.com](https://tavily.com) |
| **SearXNG** | Métamoteur de recherche auto-hébergé | Auto-hébergé (illimité) | Votre propre instance |

## Configuration

### DuckDuckGo Lite

DuckDuckGo Lite est le fournisseur de recherche **par défaut** — aucune clé API requise, fonctionne immédiatement.

1. Dans 橘子岛, allez dans **Paramètres → Recherche Web**
2. Sélectionnez **DuckDuckGo Lite** comme fournisseur de recherche
3. Aucune clé ni URL nécessaire — commencez à rechercher tout de suite

!!! note "Service au mieux"
    DuckDuckGo Lite utilise le scraping HTML de `lite.duckduckgo.com`. DDG peut modifier sa mise en page, limiter le débit ou bloquer les requêtes automatisées. Il est fourni explicitement comme une option sans clé et au mieux. Si vous avez besoin de fiabilité, configurez l'un des fournisseurs basés sur API ci-dessous.

### Brave

1. Obtenez une clé API depuis [Brave Search API](https://api.search.brave.com/)
2. Dans 橘子岛, allez dans **Paramètres → Recherche Web**
3. Sélectionnez **Brave** comme fournisseur de recherche
4. Collez votre clé API

### Serper

1. Obtenez une clé API depuis [serper.dev](https://serper.dev)
2. Dans 橘子岛, allez dans **Paramètres → Recherche Web**
3. Sélectionnez **Serper**
4. Collez votre clé API

### Tavily

1. Obtenez une clé API depuis [tavily.com](https://tavily.com)
2. Dans 橘子岛, allez dans **Paramètres → Recherche Web**
3. Sélectionnez **Tavily**
4. Collez votre clé API

### SearXNG

1. Configurez une instance SearXNG (auto-hébergée) ou utilisez une instance publique
2. Dans 橘子岛, allez dans **Paramètres → Recherche Web**
3. Sélectionnez **SearXNG**
4. Saisissez l'**URL de Base** de votre instance (par ex., `https://searx.be`)
5. La clé API est optionnelle (nécessaire uniquement si votre instance exige une authentification)

!!! warning "Instances Publiques"
    Les instances SearXNG publiques sont souvent limitées en débit ou peu fiables. L'auto-hébergement est recommandé pour une utilisation cohérente.

---

## Configuration

### Résultats Maximum

Définissez combien de résultats de recherche récupérer par requête : **1–10**. La valeur par défaut dépend de l'appareil. Plus de résultats donnent plus de contexte au modèle mais coûtent plus de tokens.

### Activer/Désactiver

Activez **Activer la Recherche Web** sur la page des paramètres de Recherche Web. Lorsqu'elle est désactivée, le modèle ne peut pas appeler l'outil de recherche web.

---

## Comment le Modèle Utilise la Recherche

Lorsque vous posez une question qui nécessite des informations actuelles ou externes, le modèle appelle automatiquement la recherche web :

1. **Recherche** : Le modèle appelle l'API de recherche avec une requête qu'il formule
2. **Récupération** : Le modèle peut optionnellement récupérer le contenu complet des pages à partir des URL des résultats
3. **Synthèse** : Le modèle lit les résultats et les intègre dans sa réponse

Vous verrez chaque recherche et récupération comme des cartes d'outils en ligne dans la conversation.

### Exemple

```text
Vous : "Quelle est la dernière version de Python ?"
Modèle : [Recherche "dernière version Python 2026"]
         [Lit le résultat]
         "Python 3.14.0 est sorti en octobre 2025..."
```

---

## Récupération Web

Au-delà de la recherche, le modèle peut récupérer et lire des pages web spécifiques. Lorsque le modèle rencontre une URL dans les résultats de recherche, il peut appeler `web_fetch` pour récupérer le contenu complet de la page :

- Le contenu récupéré est converti en markdown
- Le modèle le traite et extrait les informations pertinentes
- Les résultats de récupération sont affichés comme des cartes d'outils

---

## Considérations de Confidentialité

Lors de l'utilisation de la recherche web :

- Vos requêtes vont au fournisseur de recherche (Brave, Serper, etc.), pas à 橘子岛
- 橘子岛 n'enregistre ni ne stocke vos requêtes de recherche (sauf dans la conversation elle-même)
- L'auto-hébergement SearXNG vous donne le plus de confidentialité — les requêtes restent sur votre infrastructure
