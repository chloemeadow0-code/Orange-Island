# Proxy Réseau

Achemine tout le trafic réseau d'橘子岛 via un proxy HTTP ou SOCKS. Utile sur les réseaux restreints, pour router les requêtes via une passerelle spécifique, ou lorsqu'un fournisseur n'est accessible que via un proxy.

Le proxy s'applique à **tout** le trafic sortant : fournisseurs de chat, récupération de modèles, recherche web, embeddings, récupération de pages web et envoi de rapports de crash.

## Configuration

Ouvrez **Paramètres → Réseau → Proxy** et activez **Activer le proxy**, puis configurez :

| Champ | Description |
|-------|-------------|
| **Type** | `HTTP`, `HTTPS` ou `SOCKS5`. HTTP/HTTPS tunnellisent le trafic HTTPS via `CONNECT` ; SOCKS5 transmet au niveau socket. |
| **Hôte** | Nom d'hôte ou IP du serveur proxy (ex. `127.0.0.1`). |
| **Port** | Port du serveur proxy (ex. `7890`). |
| **Nom d'utilisateur / Mot de passe** | Optionnel. Uniquement nécessaire si votre proxy exige une authentification. |

Les modifications prennent effet immédiatement — aucun redémarrage de l'application n'est nécessaire.

## Liste de contournement

Les hôtes et plages d'adresses dans la **liste de contournement** se connectent **directement**, en ignorant le proxy. Mettez une entrée par ligne. La liste par défaut garde les adresses de bouclage et privées (LAN) en direct :

```
localhost
127.0.0.1
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
::1
```

Chaque ligne peut être :

- un hôte exact — `localhost`, `192.168.1.10`
- une plage IPv4 CIDR — `10.0.0.0/8`
- un suffixe générique — `*.example.com`

C'est pourquoi un serveur Ollama local (ex. `http://192.168.1.50:11434`) continue de fonctionner sur votre LAN tandis que tout le reste passe par le proxy.

## Remarques

- Le type **HTTPS** utilise le même protocole de proxy que HTTP (un proxy HTTP `CONNECT`) ; choisissez-le si votre proxy est étiqueté « HTTPS ».
- Le mot de passe du proxy est inclus dans les **exports de données chiffrées uniquement lorsque « Inclure les clés API » est activé**.
- Si les requêtes échouent avec des délais d'attente après l'activation du proxy, vérifiez l'hôte/port et que le type de proxy correspond à votre serveur.
