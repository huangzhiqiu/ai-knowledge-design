# Group Model

## Entity Definition

| Field | Type | Description |
|-------|------|-------------|
| group_id | string | Global unique group ID |
| conversation_id | string | Linked conversation |
| name | string | Group name |
| description | string | Group description |
| avatar | string | Group avatar URL |
| owner_id | string | Group owner |
| max_members | int | Member capacity limit |
| created_at | timestamp | Creation time |

## Group Roles

| Role | Permissions |
|------|-------------|
| owner | All permissions, transfer ownership |
| admin | Manage members, settings, mute/ban |
| member | Send messages, read history |
| muted | Read only, cannot send |

## Group Settings
- Join policy: invite / request / public
- Message permission: all members / admins only
- History visibility: all / since join
- Read receipts: enabled / disabled

## Group Events (State Changes)
Group state changes are typically modeled as events:
- `group.created`
- `member.joined` / `member.left`
- `role.changed`
- `settings.updated`
- `group.dissolved`

## Reference: Mattermost Channel Model
Mattermost `Channel` supports `team_id` scoping, with types: `O` (open/public), `P` (private), `D` (direct), `G` (group). Channel membership tracked in `ChannelMembers` table with `MsgCount`, `LastViewedAt`, `NotifyProps`.
