# Using AndroidCleanNetwork with a coding agent

The reusable workflow is the
[`androidcleannetwork` skill](skills/androidcleannetwork/SKILL.md), with its
[integration guide](skills/androidcleannetwork/references/integration.md) and
[runnable Kotlin example](skills/androidcleannetwork/assets/UserExample/).
Make these available in the **app or SDK that consumes AndroidCleanNetwork**.

## Install in the consuming project

From a checkout of this repository, using Python 3.9+:

```sh
python3 scripts/install_skill.py \
  /path/to/YourApp/.agents/skills/androidcleannetwork
```

Replace `/path/to/YourApp` with the consuming repository. The installer copies
the complete skill, guide, example and Gradle wrapper. It excludes build caches,
local settings and generated output, and refuses to overwrite an existing
destination. Review an existing installation before replacing it with a newer
copy. Commit the installed folder if the team should share it.

## Choose the agent's installation folder

The same complete `androidcleannetwork` folder works with these clients. Paths below
are relative to the **consumer app**, not to the SDK checkout.

| Client | Project destination | Personal destination | Explicit activation |
| --- | --- | --- | --- |
| Codex | `.agents/skills/androidcleannetwork` | `~/.agents/skills/androidcleannetwork` | `$androidcleannetwork` or the skill selector |
| Claude Code | `.claude/skills/androidcleannetwork` | `~/.claude/skills/androidcleannetwork` | `/androidcleannetwork` |
| GitHub Copilot | `.github/skills/androidcleannetwork` | `~/.copilot/skills/androidcleannetwork` | In VS Code agent chat, select `/androidcleannetwork` from the `/` menu |

Pass the chosen destination to `scripts/install_skill.py`. For example, for
Claude Code:

```sh
python3 scripts/install_skill.py \
  /path/to/YourApp/.claude/skills/androidcleannetwork
```

Copilot also supports `.agents/skills` and `~/.agents/skills`, so a consumer
project can share one installed copy with Codex. Avoid duplicate copies with the
same name in locations scanned by the same client. Personal paths refer to the
machine running the agent; remote sessions need the skill in their own supported
location. Commit a project installation when the team should share it. Claude
cloud project threads use account-enabled skills instead of repository skills;
see its documentation for that distinct workflow.

You can also download `androidcleannetwork-skill-1.0.1.zip` from this SDK's
[release](https://github.com/alegelos/AndroidCleanNetwork/releases/tag/1.0.1). Extract
it to a temporary directory and move the complete `androidcleannetwork` folder into
one of the locations above, without merging it into an older installation. Keep
`SKILL.md`, `references/`, `assets/` and `agents/` together. The SDK dependency is
installed separately; SwiftPM/Gradle does not install the skill for you.

Official setup references: [Codex](https://learn.chatgpt.com/docs/build-skills),
[Claude Code](https://code.claude.com/docs/en/skills),
[GitHub Copilot](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/customize-cloud-agent/add-skills)
and [VS Code skill invocation](https://code.visualstudio.com/docs/agent-customization/agent-skills).

## Activate it

In a Codex client supporting `$` skill mentions:

```text
Use $androidcleannetwork to implement these endpoints in the current app.
Follow the existing project structure. Here are the methods, paths,
request/response contracts and authentication requirements: ...
```

Use the client's skill selector where applicable. Automatic selection is allowed
when the request matches the description; explicit activation helps verify that
the intended skill is available.

For persistent guidance, merge this section into the **consumer project's**
existing `AGENTS.md`, adjusting the path if you installed elsewhere:

```markdown
## Networking

For endpoint integrations using AndroidCleanNetwork, use the androidcleannetwork
skill. Read `.agents/skills/androidcleannetwork/SKILL.md` and its relevant
references before implementing. Check the SDK version resolved by this project.
Keep Domain inputs and outputs independent of transport DTOs and responses.
```

The SDK repository's `AGENTS.md` governs work here. It does not automatically
instruct agents in every consuming app; see
[Codex instruction discovery](https://learn.chatgpt.com/docs/agent-configuration/agents-md).
Use an equivalent recognized instruction file for another agent client.

## How the skill works

`SKILL.md` supplies a name, a description of when to use the workflow, and the
integration steps. The agent can discover that description, then read the full
instructions and the linked guide/example when relevant. Explicit activation
helps ensure that this SDK's workflow is selected. It is reusable context for the
coding agent, not a runtime SDK plugin or an automatic code generator.

After installation, start a new session if the skill is absent from the selector.
Ask the agent to identify the loaded skill and SDK version, then give it a small
endpoint contract with request/response JSON. Check that it follows the app's
structure, maps transport types to Domain and runs appropriate tests. Successful
skill discovery alone does not prove that the generated integration is correct.

## Without installing a skill

Provide access to this repository and explicitly request reading
`skills/androidcleannetwork/SKILL.md`, its integration guide and relevant example
files. Use guidance compatible with the consumer's resolved SDK; do not assume
the default branch describes every installed version.

## Inputs and expected result

Provide endpoint documentation or a schema, representative JSON, base URL and
authentication requirements. Give access to the consuming project for an in-place
implementation. A sample alone cannot establish required fields or the non-null
type of a field whose only example value is `null`.

The agent should produce the required Domain contracts/models, Setup, Provider,
transport DTOs/responses, mappings and tests, adapted to the host. It should ask
only for facts that materially block correctness, and report actual build/test
results. Request a ZIP for standalone file delivery; an existing checkout should
receive implemented files directly. Explanation requests remain explanations.

## Maintain the guidance

Keep detailed contracts in the integration guide and complete code in the sample.
Update both with the SDK. Android fixtures use a classpath resolver; Kotlin
interfaces, coroutine behavior and spy members differ from the iOS SDK. Do not
copy platform-specific instructions between them without checking the source.

[AGENTS.md](AGENTS.md) lists verification commands. The skill format helps the
agent find and apply the guidance; compilation, tests and review verify the result.
