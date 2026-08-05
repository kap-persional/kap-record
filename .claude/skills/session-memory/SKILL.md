---
name: session-memory
description: Maintain a persistent project memory as a linked markdown wiki in wiki/, following Andrej Karpathy's "LLM Wiki" pattern. Two separate write modes - progress checkpoints appended automatically to wiki/log.md after every phase so an interrupted session can resume, and verified knowledge ingested into wiki/pages/ only after the user confirms the code works. Use this skill whenever the user confirms a result ("chạy ok", "test pass", "hoạt động tốt", "confirm", "done rồi", "lưu vào bộ nhớ", "works now", "that fixed it"), whenever you finish a phase of work, at the start of any session in a repo that has a wiki/ directory, and whenever the user asks to set up project memory, resume interrupted work, or ingest/query/lint the wiki. Also use it when the user asks where a past decision came from or why something was built a certain way.
---

# Session Memory (LLM Wiki)

A portable project-memory system based on Andrej Karpathy's LLM Wiki pattern
(https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f).

The problem this solves: knowledge from a working session normally evaporates
into chat history. The next session re-derives the same context from scratch,
and if a session dies mid-task, nothing records where it stopped. RAG doesn't
fix this — it re-retrieves and re-synthesizes on every question, accumulating
nothing.

The fix is to compile knowledge **once** into an interlinked markdown wiki and
then keep it current. Cross-references are already there. Contradictions are
already flagged. The wiki gets richer with every session instead of resetting.

The wiki is written in **Vietnamese with English technical terms** by default —
see "Language" below. This skill's own instructions are in English; that is
independent of what the wiki contains.

## The one thing to get right: two separate write streams

This is the core design decision. Do not collapse these into one.

| | `wiki/log.md` | `wiki/raw/` + `wiki/pages/` |
|---|---|---|
| When | **Automatically**, after every phase | **Only after the user confirms** the work is correct |
| Holds | Progress — where you got to | Knowledge — what was learned |
| Purpose | Recovery checkpoint if the session dies | Long-term reference |
| Ask first? | Never ask, just write | Never ask, but a confirmation must have happened |

The reasoning: progress and knowledge decay differently. Progress is worthless
a week later but critical five minutes after a crash. Knowledge is the reverse.
Mixing them means either the log is too sparse to resume from, or the wiki
fills with unverified guesses. Karpathy's original pattern only describes the
knowledge stream; the log stream is the addition that makes it survive
interruption.

**Principle: progress is always logged; knowledge enters the wiki only once
verified.**

## Layout

```
wiki/
├── SCHEMA.md    conventions for THIS project — read before touching the wiki
├── index.md     catalog of every page, grouped by type
├── log.md       append-only timeline + recovery checkpoint
├── README.md    orientation for human readers
├── raw/         verbatim per-session records, immutable
└── pages/       curated knowledge pages, cross-linked
```

`SCHEMA.md` outranks this skill. It is the per-project layer that the user and
you co-evolve; when the two disagree, follow `SCHEMA.md`. That is what lets one
skill serve projects with very different conventions.

## Bootstrap — when `wiki/` does not exist

If the user asks to set up project memory and there is no `wiki/` directory,
create one:

```bash
python3 {skill_dir}/scripts/init_wiki.py --repo <repo-root>
```

This copies the templates from `assets/` into `wiki/`, creates `raw/` and
`pages/`, and appends the `init` entry to `log.md` with a real timestamp. Pass
`--force` only to overwrite an existing wiki — check with the user first, since
that discards accumulated memory.

Then wire the conventions into the project's `CLAUDE.md` so they survive into
sessions where this skill isn't invoked by name. `assets/CLAUDE-snippet.md`
holds the text — append it if `CLAUDE.md` exists, create the file if it
doesn't. Skip this only if the user objects; without it the memory system goes
dormant whenever the skill doesn't trigger.

Finally, walk the user through `wiki/SCHEMA.md` and adjust the page types to
their domain. The default types are generic on purpose.

## 1. Checkpoint — automatic

Run this the moment you finish a unit of work, without being asked and without
asking permission. Qualifying moments: a planned step is done, a related group
of files is edited, a test or build finishes, a commit lands, or you hit a
blocker and have to stop.

```bash
python3 {skill_dir}/scripts/log_entry.py phase "what you did" --status DONE
```

The script stamps the real clock time and enforces the format. Use it rather
than hand-writing entries — hand-written timestamps drift toward guesses, and a
wrong timestamp destroys the log's value as a recovery record.

When the work is unfinished, say so and say what comes next:

```bash
python3 {skill_dir}/scripts/log_entry.py phase "refactoring the parser" \
  --status "IN PROGRESS" --next "extract tokenize() then re-run tests"
```

If the script is unavailable, get the time from `date '+%Y-%m-%d %H:%M'` and
append by hand — never invent a timestamp.

**Never write `DONE` for work that isn't done.** A log that overstates progress
is worse than no log, because the next session trusts it and skips the gap. The
whole value here is that someone returning after a crash can believe what they
read.

At the **start of every session** in a repo with a wiki, read the recent log
before doing anything else:

```bash
grep "^## \[" wiki/log.md | tail -10
```

If the last entry is `IN PROGRESS` or `BLOCKED`, resume from its `- next:` line
rather than restarting the task.

## 2. Ingest — after the user confirms

Trigger on explicit confirmation that the work is correct: "chạy ok", "test
pass", "hoạt động tốt", "confirm", "done rồi", "lưu vào bộ nhớ", "works now",
"that fixed it".

Absent confirmation, do not ingest. The wiki's usefulness rests on everything in
it having been verified — one unverified page and the reader has to re-check
everything.

Do the whole sequence, then report once. Don't ask for approval step by step.

1. **Reconstruct the session**: what the user asked for; decisions and
   trade-offs settled; files created or changed and why; the command or test
   that proved it works.
2. **Re-read the code before writing any specific claim about it.** Open the
   files you are about to describe and check every concrete detail against
   them — numbers, ordering, conditions, which branch does what, what happens
   on the last iteration. Conversation memory is a worse source than the file
   on disk: it compresses, and it remembers intent rather than what was
   actually written. A vague page is merely thin, but a page that states a
   wrong number reads as authoritative and gets believed for months. If a
   detail resists quick verification, describe the behavior at a level you can
   stand behind instead of guessing at specifics.
3. Write that to `wiki/raw/<YYYY-MM-DD>-<slug>.md` as a new file. Never edit an
   existing `raw/` file — it's the immutable record.
4. Update `wiki/pages/`: edit an existing page when the new information extends
   it, create a page when the concept has none. Add cross-links both ways —
   a page nothing links to is invisible.
   - When new information **contradicts** what a page says, record the
     contradiction instead of silently overwriting:
     `⚠️ Cập nhật [YYYY-MM-DD]: khác với ghi nhận trước vì <reason>`.
     Silent overwrites are how a wiki quietly becomes wrong: the old claim
     disappears along with the evidence that anything changed.
5. Update `wiki/index.md` for pages you touched.
6. Log it: `python3 {skill_dir}/scripts/log_entry.py ingest "<title>"`.
7. Report briefly: what was recorded, into which pages.

Judgment about what deserves a page matters more than volume. A wiki of fifty
sharp pages beats one of five hundred where most restate what reading the code
would show. `SCHEMA.md` carries the project's criteria; the general shape is
that design decisions, hard-won debugging, non-obvious behavior, and business
constraints are worth pages, while syntax fixes and anything obvious from the
code are not.

## 3. Query

When the user asks about earlier work, a past decision, or why something is
built the way it is: read `wiki/index.md` to locate candidate pages, open them,
and answer **with file paths as citations** so the user can verify and keep
reading. If the synthesized answer has lasting value, offer to file it as a new
page — Karpathy's point is that good answers shouldn't vanish into chat history
any more than sources should.

## 4. Lint

When asked to health-check the wiki, look for: contradictions between pages,
orphans (nothing links to them), claims superseded by newer sources, and
concepts referenced repeatedly that have no page of their own. Report findings
and let the user decide; don't rewrite pages unprompted, since resolving a
contradiction usually needs knowledge that isn't in the wiki yet.

## Language

Default is bilingual: English for identifiers, file names, and technical terms
(`retry policy`, `race condition`, `idempotency key`); Vietnamese for
explanation, reasoning, and trade-offs. Page file names are English kebab-case
(`booking-api.md`, not `api-dat-cho.md`) so links stay stable and greppable.

This is recorded in each project's `SCHEMA.md` and can be changed there per
project without touching the skill.
