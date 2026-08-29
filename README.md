# insmpbase

Paper plugin that syncs Minecraft ranks from the INSMP auth API into LuckPerms groups.

On join (and on demand) it looks the player up in `https://auth.insmp.org/api/whitelist`,
reads the role field off their record, and puts them in the mapped LuckPerms group.

## Setup

1. Install **LuckPerms** on the server. The plugin hard-depends on it and will not enable without it.
2. Create the groups you plan to map, e.g. `/lp creategroup savanna`.
3. Start the server once to generate `plugins/basePlugin/config.yml`.
4. Put your API token in `api.token`, or leave it blank and set the `INSMP_API_TOKEN`
   environment variable instead.
5. Fill in `sync.groups` — one line per API role value.
6. `/insmp reload`.

## Which field picks the group

`sync.role-field` decides. It defaults to `nation_name`, because that is the only role-like
field in the API payload today:

```json
{
  "minecraft_uuid": "00000000-0000-0000-0009-01fb27410bfc",
  "nation_name": "Savanna",
  "minecraft_username": "Yuuki795",
  "minecraft_account_type": "bedrock",
  "minecraft_xuid": "2535452997389308"
}
```

If the API starts returning a dedicated `role` field, change one line:

```yaml
sync:
  role-field: "role"
```

Values are matched case-insensitively, so `Savanna` matches a `savanna:` key.

## Commands

`/insmp` — permission `insmp.admin`, default op.

| Command | Effect |
| --- | --- |
| `/insmp sync [player]` | Re-pull the API and re-sync one player (yourself if omitted). Works for offline players too, as long as the API knows their UUID. |
| `/insmp sync all` | One API pull, then re-sync everyone online. |
| `/insmp lookup <player>` | Show the API record and the group it resolves to. |
| `/insmp status` | Cache size, age, and the last API error. |
| `/insmp reload` | Re-read `config.yml`. |

## Behaviour notes

- **Bedrock works by UUID.** Floodgate packs the XUID into the low bits of the player's UUID,
  which is exactly what the API returns as `minecraft_uuid` for bedrock accounts, so UUID
  matching covers Java and Bedrock alike. Username is used as a fallback.
- **The whole list is pulled once and cached**, not once per join. Background refresh is
  `cache.refresh-seconds`; a join refreshes first only if the cache is older than
  `cache.max-age-on-join-seconds`.
- **A failing API never strips anyone.** If a refresh fails the previous snapshot keeps serving
  and the error is logged (and shown by `/insmp status`).
- **Only mapped groups are touched.** With `sync.exclusive: true` the plugin removes the *other*
  groups listed in `sync.groups` and adds the right one. Staff, donor, and any other group it
  was never told about are left alone.
- **`set-primary-group` writes LuckPerms' *stored* primary group.** LuckPerms only reads that
  value when its own `primary-group-calculation` is set to `stored`. On the default setting,
  `parents-by-weight`, LuckPerms derives the primary group from group weights instead - so give
  your mapped groups weights (`/lp group tundra setweight 10`) or switch LuckPerms to `stored`.
- All HTTP and all LuckPerms writes happen off the main thread. Kicks and player messages hop
  back to the player's scheduler.

## Building

```bash
./gradlew build
```

`./gradlew runServer` starts a 1.21.11 Paper server with LuckPerms downloaded automatically.
