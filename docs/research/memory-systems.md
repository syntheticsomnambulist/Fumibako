# Persona memory: importance, emotion, forgetting and reflection

Research brief for the AI Harness memory design discussion. Prepared 5 November 2026. This is input for a conversation, not an implementation plan.

---

## 0. Summary

**What the field does**

- **Relevance does most of the work everywhere.** Every system I looked at ranks mainly by how well a memory matches the moment (keywords, embeddings, graph links). Importance and recency are small nudges on top. Some of the strongest results come from making *search* better, not from scoring memories better. One example is Letta's plain-file agent that searches iteratively.
- **Model-chosen importance scores are popular but barely tested.** Generative Agents made the 1-10 "poignancy" score famous, but the paper never tests whether that score helps. Its released code also uses different weights from the paper. MemGPT/Letta, Mem0, A-MEM and Zep don't ask the model for an importance number at all. Commercial companions that expose priority use **three levels** (Nomi: High / Standard / Low) or user signals (Kindroid: favourite, deprioritise; Character.AI: pin).
- **What does carry signal is emotional intensity and usage.** One long user study (LUFY: people chatted for 2+ hours across 4 sessions) found emotional arousal was by far the strongest predictor of which memories mattered. In LUFY's learned weights, arousal (2.76) far outweighed the LLM's own importance rating (0.44). Prioritising arousing memories while forgetting about 90% of the conversation improved user ratings. A 2026 preprint found the same thing: emotional intensity and "who said it" (the user vs the model) were the most useful factors for deciding what to keep.
- **People disagree about what is "important".** In LUFY, pairs of human annotators agreed on which utterances were important only about 23% of the time. A fine 1-10 scale implies precision that isn't there.
- **LLMs are uneven at numeric ratings.** They cluster on a few values, favour round numbers, and vary between runs. Strong models handle 1-10 reasonably well. That gets riskier across a mix of providers and small local models.
- **Decay is common in products but rarely validated.** Mem0 shipped an opt-in, access-based decay that only re-orders results (0.3x to 1.5x) and never hides anything. MemoryBank's Ebbinghaus curve was never tested by its authors. Nomi and Kindroid keep an "active" working set and let the rest fade from automatic recall while keeping it searchable. Nobody I found hard-deletes memories automatically.
- **Emotion-tagged memory is a young area.** There is only a handful of small papers. Emotional RAG reranks memories by emotional similarity, which helps role-play characters stay in character. A 2026 robot study multiplies memory strength by emotional intensity. PsychoAgent filters by topic first and then reranks by salience. None shows a large, clean win for mood-congruent recall. The safe pattern is "match the topic first, use emotion as a tie-breaker".
- **Revising a memory's emotion has a good human analogy and a good agent precedent.** In people, recalling a memory makes it editable again (reconsolidation), and we remember past feelings through our present view (Levine & Safer). Negative feelings fade faster than positive ones (the fading affect bias). Nomi's design keeps underlying memories read-only while the summaries built on them can change. A-MEM updates the *descriptions* of old notes but never their original content. Both point to the same rule: **keep what was felt at the time, and store the reinterpretation beside it.**

**What I'd pick for AI Harness** (detail in section 5)

1. **Importance becomes three named levels** that the persona picks in words: `minor`, `normal` (the default) and `major`. `major` is rationed. The rest of what the score used to do is *derived*: emotional intensity, how often the memory is actually used, and whether the user wrote or confirmed it. `pinned`/CORE stays as the separate "always in the prompt" switch.
2. **Emotion is stored as two words**: `feltMood`, which never changes once saved, and `mood`, the current view, which can be revised. A revision carries a short reason and a date. From the word, the app looks up a coarse valence and intensity in a small built-in table, so no numbers are asked of the model. Intensity slows decay and gives a small ranking bonus. Mood-congruent recall stays off at first, then becomes a capped tie-breaker if tried.
3. **Decay becomes "strength from use"**: each memory has a half-life that depends on its level and grows when the memory is genuinely used. Automatic top-k recall does *not* count as use, which ends today's self-reinforcing loop. Faded memories become *dormant*: hidden from automatic recall, still searchable and visible, never deleted automatically. Contradicted facts are marked *superseded* rather than overwritten.
4. **The heartbeat works like "sleep"**: fold conversation into episodes, reflect when emotional load builds up, revisit what was recently touched (the only time emotions get revised), tidy duplicates and contradictions, and roll old episodes into chapters while keeping the originals.

---

## 1. The app today, and what the numbers actually do

From `core/src/main/kotlin/dev/aiharness/core/memory/MemoryService.kt`:

- Recall score = `0.65 * relevance + 0.20 * (importance / 10) + 0.15 * recency`, with `recency = 0.5^(days since max(lastAccessedAt, updatedAt) / 14)`. Relevance is BM25, mixed 0.3/0.7 with rescaled embedding cosine when embeddings are on. Candidates below `minRelevance` are dropped before scoring.
- **In practice, importance is a small nudge.** At the default of 5 it adds 0.10. Going from 5 to 9 adds 0.08, which is about the same as a 0.12 gain in relevance. So the persona's careful 1-10 choice mostly decides near-ties.
- **Merging only ever raises importance.** On a near-duplicate merge the code keeps `max(existing.importance, new)`. Over a long relationship, importance can only creep upward, never come back down.
- **Recall feeds itself.** `retrieve()` marks every returned memory as accessed, which resets its recency. A memory that gets recalled becomes more recent and so more likely to be recalled again. This is a mild rich-get-richer effect. Mem0 limits the same effect (see 2.3).
- **Nothing fades out of the candidate pool.** Every memory competes on every turn. That's fine at hundreds of memories. At thousands it means more noise near the top and more BM25 work per turn on the phone.
- Episodes are saved at a fixed importance of 6 (`HeartbeatEngine.recordEpisodes`, `ContextBuilder`).

None of this is wrong. It just means the 1-10 scale isn't buying much today.

---

## 2. How notable systems handle importance and salience

### 2.1 Generative Agents (Park et al., UIST 2023)

- **What's stored:** a "memory stream" of natural-language observations, plans and reflections, each with a creation time and a last-access time.
- **Importance:** asked once, when the memory is created, with this prompt: *"On the scale of 1 to 10, where 1 is purely mundane (e.g., brushing teeth, making bed) and 10 is extremely poignant (e.g., a break up, college acceptance), rate the likely poignancy of the following piece of memory."* Examples from the paper: "cleaning up the room" scored 2 and "asking your crush out on a date" scored 8.
- **Retrieval:** recency, importance and relevance are each min-max normalised to [0, 1] and added. The paper sets every weight to 1, with recency as exponential decay of 0.995 per sandbox hour since last retrieval.
  - The **released code differs**: `gw = [0.5, 3, 2]` (recency, relevance, importance). The comment reads *"[1, 1, 1] tends to work decently, but in the future, these weights should likely be learned"*. Recency in the code is `0.99^rank` over memories sorted by last access, not by hours.
  - On my reading of `retrieve.py`, the list is sorted oldest-first before the decreasing values are assigned. That would give the *least* recently accessed memory the highest recency score. I could not find an issue or erratum confirming this, so treat it as unverified. Either way, the weighting was heuristic.
- **Reflection trigger:** reflect when the sum of importance scores of recent events passes 150. In practice that meant about two or three times a simulated day. Reflections ask "what are the 3 most salient high-level questions?", retrieve memories for each, and store insights that **cite their evidence** ("... (because of 1, 2, 8, 15)") as pointers to the source memories.
- **Evidence:** the ablation removed whole memory *types*. Believability ratings (TrueSkill) were: full system 29.89, no reflection 26.88, no reflection or planning 25.64, human crowdworkers 22.95, no memory at all 21.21. There is **no ablation of the importance score or of recency weighting**. Known failure modes include retrieving incomplete fragments and "embellishing" memories with invented details.

**Takeaway:** importance as a *trigger* for reflection is the clever part. As a ranking term it was never isolated.

### 2.2 MemGPT / Letta

- **What's stored:** "core memory" blocks that sit in the prompt. Each has a label, a description and a character limit (e.g. 5,000 characters) and can be read-only. "Recall memory" is the full message history, searchable. "Archival memory" holds saved passages, searched by embedding.
- **Importance or decay:** none. The agent edits its own memory through tools. When the context reaches about 70%, the system warns the model ("memory pressure") so it can save what matters. At 100%, about half the context is evicted into a recursive summary, and the evicted messages stay searchable.
- **Evidence:** on the "deep memory retrieval" test, GPT-4 went from 32.1% to 92.5% accuracy with MemGPT.
- **Sleep-time agents (Letta, April 2025):** a second agent edits the primary agent's core memory in the background. The primary agent loses its core-memory editing tools. Letta's own explanation: *"Memory formation in MemGPT is incremental, so memories may become messy and disorganized over time. Sleep-time agents on the other hand can continuously improve their learned context."* They suggest a **stronger model for the sleep-time agent**, since it isn't latency-bound.
  - The accompanying paper measured math and reasoning tasks (about 5x less compute at answer time for the same accuracy), not companion memory quality.
- **"Is a filesystem all you need?" (Letta, August 2025):** an agent that just put the conversation in a file and searched it with grep and semantic search scored **74.0%** on the LoCoMo benchmark with gpt-4o-mini. That beats the 68.5% Mem0 reported for its own best variant. Letta's conclusion: *"memory is more about how agents manage context than the exact retrieval mechanism used."*

### 2.3 Mem0

- **What's stored:** extracted "facts" with metadata and embeddings, plus an optional entity graph.
- **Update policy (paper, 2025):** for each new candidate fact, find similar memories, then let the LLM choose ADD, UPDATE, DELETE or NOOP. The graph variant marks contradicted relations *invalid* instead of deleting them, so the timeline survives.
  - **Current docs** describe the automatic path as *additive*: *"If a user says, 'I moved from Austin to Seattle,' Mem0 can store the new fact without silently rewriting the old one."* Explicit update and delete are left to the application.
- **Importance:** no stored score. The extractor is told to keep salient facts.
- **Decay (Platform feature, opt-in):** each memory keeps its last 20 retrieval times. At search time this history becomes a multiplier between 0.3x and 1.5x: just accessed is about 1.5x, idle for weeks 0.4 to 0.6x, idle for months about 0.3x. In the docs' words, *"It is a soft ranking bias, never a filter."* The docs describe it as *"deliberately the simplest version of decay we could ship"*. Planned next: weighting by category, so health facts fade more slowly than trivia. There is also a "memory expiration" feature that stops a memory surfacing after a set date without deleting it.
- **Evidence (LoCoMo, LLM-judge score):** Mem0 66.9, Mem0 with graph 68.4. But simply putting the **whole conversation in context scored 72.9**. Mem0's win is in cost and latency: 91% lower p95 latency and over 90% fewer tokens.
  - Zep disputed Mem0's numbers for Zep (their corrected Zep score is 75.1). Zep also criticised LoCoMo itself: conversations fit in modern context windows, there are no "knowledge update" questions, and some answers are wrong.
  - I found no published evaluation of Mem0's decay feature.

### 2.4 A-MEM (Xu et al., 2025), Zettelkasten-style

- **What's stored:** atomic notes, each with the original content, a timestamp, LLM-written keywords, tags and a context description, an embedding, and links to related notes.
- **Importance or decay:** none.
- **"Memory evolution":** when a new note arrives, the LLM may update the *context, keywords and tags* of its neighbouring notes. The original content is never changed. This is the closest agent precedent to "revise the interpretation, keep the record".
- **Evidence (ablation, LoCoMo F1):** multi-hop questions scored 9.7 with neither linking nor evolution, 21.4 with linking only, and 27.0 with both. Temporal questions scored 24.6, 31.2 and 45.9. So updating the interpretation of old memories measurably helped.

### 2.5 MemoryBank / SiliconFriend (Zhong et al., 2023)

- **What's stored:** raw conversations, daily event summaries rolled up into a global summary, and daily personality notes rolled up into a global profile. This is a summarisation hierarchy.
- **Decay:** an Ebbinghaus-style retention `R = e^(-t/S)`. Strength S starts at 1. Each time the memory is recalled, S goes up by 1 and t resets to 0.
- **Evidence:** the authors call it *"an exploratory and highly simplified memory updating model."* LUFY later pointed out that MemoryBank *"did not include experiments to validate their proposed concept."* In LUFY's user study, MemoryBank did no better than plain retrieval.

### 2.6 Zep / Graphiti (Rasmussen et al., 2025)

- **What's stored:** three layers. *Episodes* are the raw messages, kept intact as a lossless source. *Facts* are edges between entities. *Communities* are summaries of clusters of entities.
- **Time instead of decay:** every fact carries *two* timelines. One records when it was true in the world (`valid` / `invalid`); the other records when the system learned or retired it. When a new fact contradicts an old one, the LLM sets the old one's end date. Newer information wins, and history is kept.
- **Salience:** no importance score. One available reranker boosts facts that are *mentioned more often* in conversation, which is importance derived from frequency. Others include reciprocal rank fusion, diversity (MMR), graph distance and a cross-encoder.
- **Evidence (LongMemEval):** with gpt-4o, accuracy went from 60.2% (full context) to 71.2% (Zep), with the context shrinking from 115k to 1.6k tokens.
  - Knowledge-update questions improved with gpt-4o (78.2 to 83.3) but got slightly *worse* with gpt-4o-mini (76.9 to 74.4). The authors note smaller models struggle with the temporal data. **This matters for an app that also runs small local models.**

### 2.7 LangMem (LangChain)

- **What's stored:** three types. Semantic memory holds facts, either as an open collection or a fixed-schema profile. Episodic memory holds past experiences and summaries. Procedural memory holds the agent's own instructions.
- **Two ways to form memories:** "hot path" (the agent saves during the conversation, which adds latency) or "background/subconscious" (reflect after the conversation, aiming for higher recall). The app already does both: tool calls during a turn, and the heartbeat.
- **Importance:** the docs say *"Recall should combine similarity with 'importance' of the memory, as well as the memory's 'strength', which is a function of how recently/frequently it was used."* The examples only tell the extractor to "indicate their importance" in text. I did not find a built-in scoring implementation. Also not found: any evaluation of this.

### 2.8 Consumer products (only what is publicly documented)

| Product | What's documented | Importance / priority | Decay / forgetting |
|---|---|---|---|
| **ChatGPT** | "Saved memories" (explicit) plus "reference chat history" (since 10 April 2025; free users from 3 June 2025). Reverse-engineering suggests a summary or "dossier" of past chats injected into each new chat, not search (Rehberger, via Willison). | Not documented. The FAQ says it keeps what it "determines are most important". | October 2025 "automatic memory management", reported as prioritising by recency and frequency so "memory full" stops happening. *I could not open OpenAI's pages; these details come from search summaries and press coverage.* |
| **Character.AI** | Pinned messages, "auto-memories" (paid tier), and "chat memories": a 400-character fixed text box (official blog, May 2025). | User pins. | Old context drops out of the window. A 2026 "Story Memory"/"Facts" redesign is reported by secondary sites only; *unverified*. |
| **Replika** | Memory tab of facts the user can view, add and delete, plus a "deeper system" built from conversation patterns; also a Diary. | Not documented. | Not documented. *Help page blocked to me; details from search snippet.* |
| **Kindroid** (official docs) | Persistent fields (backstory, key memories, directives, "Learned Context"); "Cascaded Memory" (hierarchical medium-term memory); long-term memory consolidated "at regular intervals"; keyphrase-triggered journal entries. | Cascaded memory keeps things *"based on their significance and timing"*. Users can **favourite** messages, which then count for more when memories form, and **Deprioritize** a recalled memory. Learned Context drops "the least important older items" when full (800 / 1,200 / 800 character areas). | Long-term recall is sifted by *"relevance, recency, and diversity"*. Lower fidelity for older material (*"akin to remembering something more vaguely if it was 1 month ago than 1 day ago"*). Suggested journal entries need user approval (at most one per day). |
| **Nomi** (official wiki) | Short, medium and long-term memory; an "Identity Core" (the Nomi's own evolving self-understanding, which users can't view or edit); "Mind Map" entries, which are dossiers *derived from* memories. | **Three levels: High (up to 10), Standard ("dynamically decides importance"), Low ("never considered").** Up to 50 active entries per category, 150 in total. 2 to 5 entries recalled per response. | Entries outside the active set "will start to fade away" from the Mind Map but remain in long-term memory. *"The memories themselves are never editable"*; editing a dossier "would be like editing a summary of something that happened, not editing what happened". |

### 2.9 Does importance or decay scoring actually help?

| Evidence | What it shows |
|---|---|
| Generative Agents | Reflection and memory types matter. Importance and recency weights were never isolated, and the code weights were hand-tuned. |
| LUFY (Sumida et al., 2025; 2+ hour human studies) | Learned weights: **arousal 2.76**, retrieval count 1.02, LLM-estimated importance 0.44, surprise -0.28. Removing arousal or the LLM importance each dropped F1 from 58.3 to about 51.5; removing retrieval count dropped it to 55.3. Overall user rating: 3.78 vs 3.63 (plain retrieval) and 3.58 (MemoryBank). Precision over 17% better than plain retrieval. **Pairwise human agreement on "important" was about 23%.** |
| Multi-factor value model (Chen & Cheng, 2026 preprint) | Choosing what to keep (30% budget, LongMemEval): learned mix 0.77, uniform weights 0.66, best single factor 0.52, **recency alone 0.37**. Dominant factors: reliability (user-stated beats model-stated) and emotional intensity. Both were computed without an LLM. |
| Mem0 decay | Shipped and documented, no published evaluation. |
| MemoryBank | Never validated. No better than plain retrieval in LUFY's study. |
| Letta filesystem | Iterative search by the agent beat specialised memory scoring on LoCoMo. |
| LongMemEval (Wu et al., ICLR 2025) | Commercial assistants and long-context models lose about 30% accuracy over sustained chats. *Replacing* raw history with summaries or facts loses information and hurts accuracy. Using summaries as an *index* over the raw text helps. ChatGPT *"tended to overwrite crucial information as the chat continues."* |
| LLM ratings (G-Eval; Stureborg et al., 2024) | Scores cluster: "one digit usually dominates", models output integers only, and favour round numbers (90, 95) on a 1-100 scale. With GPT-4, 1-10 actually correlated *best* with humans among the scales tried (0.43 vs 0.34 for 1-5). But consistency between samples was low. Strong models can use 1-10; whether a mix of models can is the open question. |

**My reading:** there is no good evidence that a model-chosen 1-10 score improves recall. There is moderate evidence that emotional intensity, usage and provenance carry real signal. Decay mainly earns its keep by keeping the candidate pool clean and the companion feeling natural, not by improving question-answering accuracy.

---

## 3. Emotion in memory

### 3.1 Human memory research (practical version)

- **Arousal, more than pleasantness, makes memories stick.** The amygdala boosts how well emotionally *arousing* experiences are stored (McGaugh, 2004). Kensinger & Corkin (2004) found two routes. Arousing material is enhanced automatically; merely pleasant or unpleasant but calm material is enhanced only with deliberate attention. **Design consequence: intensity ("how strongly felt") should drive memory strength, not just positive vs negative.**
- **Emotional memories feel more accurate than they are.** For 9/11 memories, vividness and confidence stayed high while consistency declined *as fast as* everyday memories (Talarico & Rubin, 2003). Emotion also narrows focus: the central detail is kept and the background lost (Payne et al., 2008). **Consequence: a strong emotion tag shouldn't make the persona *more certain* about details.**
- **Mood-congruent recall.** People recall material that matches their current mood more easily (Bower, 1981). The stricter "mood-dependent" effect (recall is best in the same mood as learning) is fragile in the lab. It is most reliable with real moods and self-generated events (Eich, 1995). In depression, recall tilts towards negative material (Matt, Vázquez & Campbell, 1992 meta-analysis; implicit memory, Gaddy & Ingram, 2014). This is part of how low mood keeps itself going. **Consequence: mood-congruent retrieval can create a melancholy loop.**
- **Feelings fade unevenly.** The fading affect bias: the feeling attached to negative memories fades faster than for positive ones, a healthy coping pattern that is disrupted in dysphoria (Walker, Skowronski & Thompson, 2003). **Consequence: a persona whose old hurts soften over time is psychologically realistic, and the reverse should be the exception.**
- **We remember past feelings through present eyes.** Recalled emotions are partly rebuilt from current appraisals and goals (Levine & Safer, 2002). For example, students who later got good grades underestimated their pre-exam anxiety.
- **Recalling a memory makes it changeable.** Reactivated memories become labile and must be re-stored, so they can be altered at that moment (Nader, Schafe & LeDoux, 2000). **Consequence: tie emotion revisions to moments when a memory is actually recalled.**
- **Episodes are judged by their peak and their end.** Retrospective ratings track the most intense moment and the final moments, not the average or the duration (Kahneman et al., 1993). **Consequence: an EPISODE's default emotion should be its peak mood, or its end mood.**
- **Sleep consolidates selectively.**
  - Sleep keeps emotional elements at the expense of neutral background (Payne et al., 2008).
  - Sleep strengthens what you *expect to need later* (Wilhelm et al., 2011).
  - Walker & van der Helm (2009) *propose* that REM sleep reprocesses emotional experiences so that next-day reactivity calms down ("overnight therapy"). This is a hypothesis with supporting evidence, not settled fact.
  - **Consequence: the heartbeat is a natural place to keep emotional or future-relevant memories, roll up the rest, and let emotions settle.**
- **Retrieval practice strengthens memory**, more than re-reading does (Roediger & Karpicke, 2006). Memory availability tracks the statistics of future need: a power law of recency and frequency (Anderson & Schooler, 1991). This is the basis of ACT-R's activation formula `B = ln(sum over past uses of t^-0.5)`.

### 3.2 AI systems that tag memories with emotion

| System | Emotion representation | How it's used | Evidence |
|---|---|---|---|
| **Emotional RAG** (Huang et al., 2024) | 8 Plutchik emotions, each rated 1-10 by GPT-3.5, for both the query and each memory | Combine semantic and emotional similarity (add or multiply), or retrieve semantically and then rerank by emotion ("semantic-first") | Better personality-consistency scores (MBTI/BFI tests) on three role-play datasets. Semantic-first did best on one test, additive on the other. Gains were larger with weaker models. Not a measure of recall accuracy or user satisfaction. |
| **LUFY** (2025) | Arousal of the user's utterance, from a fine-tuned classifier (no LLM) | Main factor in importance; decides what is kept and what is forgotten | User study above. Arousal was the strongest factor. |
| **Robot "Kim"** (Aschenbrenner et al., 2026 preprint) | Valence and arousal in [-1, 1] from the LLM when storing; intensity = distance from neutral | Strength = time + access + recency x (1 + 0.5 x intensity). Topic similarity is weighted x50 so strength only breaks ties. **Emotion makes memories more resistant to decay.** | 43 people rated videos: memory vs no memory raised perceived sociability (d = 0.60) and warmth (d = 0.56). The emotion term's effect was not isolated. |
| **PsychoAgent** (2026 preprint) | Separate stores for facts and feelings | Filter by topic, then rerank by salience | Retrieved more conflict-critical memories (0.93 vs 0.50 and 0.67). Differences in human ratings were not significant. |
| **Multi-factor value** (2026 preprint) | Emotional intensity from a lexical extractor | Decides what to keep | Second-largest learned weight. |
| **Kindroid, Nomi** | Not documented as emotion tags. Kindroid says it keeps "exceptional experiences" more vividly. | n/a | n/a |

**Common pattern:** emotion is used either (a) as a *strength or decay modifier* (LUFY, Kim, multi-factor), with the best evidence, or (b) as a *reranker after topic filtering* (Emotional RAG semantic-first, PsychoAgent), with modest evidence. Nobody lets emotion pull in memories that don't fit the topic.

None of these systems tags memories with the *agent's own* mood. They tag the user's or the content's emotion. AI Harness's idea of storing the persona's self-chosen mood is unusual. It fits the letters format well, because it's the persona's diary-like stance, not a guess about the user.

### 3.3 Letting the persona revise a memory's emotion

**What supports it**

- Reconsolidation: recalled memories are open to change, and in people the felt quality of a memory really does shift with later events and current appraisals. The fading affect bias is the healthy direction of travel.
- A-MEM's "memory evolution" rewrites the interpretive fields of old notes as new information arrives, and it measurably helped (2.4).
- Nomi's split: raw memories stay read-only, while dossiers and the Identity Core change. Zep and Mem0 keep superseded facts with dates rather than erasing them.
- Generative Agents' reflections cite the memories they came from. That is a model for attaching a *reason* to a reinterpretation.

**Risks**

1. **Rewriting history:** the persona's "now" view replaces what it felt then. Levine & Safer is exactly this bias in people. The persona's sense of its own story flattens ("it was always fine").
2. **Drift and flip-flopping:** weaker models may re-tag a memory on every heartbeat, or drift towards their default upbeat tone. In Generative Agents, instruction tuning made agents "overly cooperative".
3. **People-pleasing:** after a conflict the persona revises "hurt" to "understanding" because that's agreeable, not because anything was resolved.
4. **Rumination loops,** if emotion also drives recall: a sad mood pulls up sad memories, which feed a sad mood.
5. **Manipulation and poisoning:** a user, or injected text from a link or document, talks the persona into reframing memories. The SpAIware research showed memories can be planted through ChatGPT's memory tool from untrusted content.
6. **False confidence:** strong emotion tags don't make details more accurate (Talarico & Rubin).

**Mitigations**

- **Two fields: `feltMood` never changes, `mood` is revisable.** The prompt shows both when they differ ("felt hurt, now at peace"). That's richer storytelling *and* an audit trail.
- **Revise only on recall:** only memories recalled in this turn, or surfaced for reflection by the heartbeat, can be revised.
- **A reason is required** ("we talked it through in March"), plus a revision date. Rate-limit to once per memory per week, say. Freeze or flag a memory after a few revisions.
- **Revisions change ranking only through intensity bands.** A revision can't change the memory's content or kind.
- **Users see it:** the memory screen shows felt vs now and offers "reset to felt".
- **Expected direction:** prompt the persona that feelings commonly soften with time and resolution. Treat revisions towards *more* negative as needing a concrete new event.

---

## 4. Forgetting and consolidation

### 4.1 Decay schemes, roughly in order of complexity

1. **Fixed half-life on last access.** This is the current app; Generative Agents works the same way per rank. Simple. Feeds itself if automatic recall refreshes it.
2. **Strength grows with recall** (MemoryBank: `e^(-t/S)`, S + 1 per recall). Simple and spaced-repetition-like. Never validated by its authors.
3. **ACT-R style activation from the history of uses** (`ln sum t^-0.5`). The most principled: it captures recency, frequency and spacing together. Needs a short list of use times; Mem0 keeps the last 20.
4. **Bounded multiplier on relevance** (Mem0: 0.3x to 1.5x). It can reorder but never hides a strong match. A good safety property.
5. **Decay modulated by salience:** emotion multiplies strength (Kim), category weighting (Mem0's roadmap), or a "High" pin (Nomi).
6. **Validity in time** instead of decay (Zep): facts are either current or superseded. The right model for *changing facts* (moved house, new job) as opposed to fading details.

### 4.2 Spaced recall and rehearsal

Retrieval practice strengthens memories, and sleep favours what's expected to matter later. For a companion, that suggests the heartbeat occasionally *revisits* a few important but dormant memories: an anniversary, an unresolved worry. Then the persona can bring them up naturally ("I was thinking about your exam...").

Generative Agents' reflection on recent memories and Letta's sleep-time agent are versions of this. **Risk:** repetitive callbacks and context bleed (Willison's complaint about ChatGPT). Use a cooldown, and keep it to one or two memories per heartbeat.

### 4.3 Offline consolidation, the heartbeat as "sleep"

- **Letta:** background memory editing gives cleaner memory than editing during conversation. A stronger model can do it because latency doesn't matter.
- **LangMem:** "subconscious" formation after the conversation is quiet gives better recall of extracted facts.
- **Kindroid:** consolidates long-term memory at regular intervals.
- **Generative Agents:** reflects when accumulated importance passes a threshold. That is event-driven, not clock-driven.

### 4.4 Summarisation hierarchies

- MemoryBank rolls daily summaries into a global summary and daily personality notes into a global profile.
- Kindroid's cascaded memory is hierarchical, with lower fidelity for older material.
- Zep uses episodes, then facts, then communities.
- MemGPT keeps a recursive summary of evicted messages.

**Caution from LongMemEval:** summaries are good *keys* for finding things but lossy *values*. Keep the originals, mark them dormant or archived, and link the summary to its sources.

### 4.5 What tends to go wrong in long-running companions

- **Bloat and mess:** incremental memory gets "messy and disorganized" (Letta). ChatGPT users hit "memory full" before the October 2025 change. Kindroid admits compression trades fidelity for cost.
- **Stale and contradictory facts:** this is the "knowledge update" weakness in LongMemEval. ChatGPT overwrote crucial information. Mem0 moved its automatic path to "additive". Zep uses validity dates.
- **Missed recall:** Nomi's wiki says *"Information may still exist in memory without being retrieved at the right moment."* Kindroid says long-term memory is "recalled less reliably".
- **Too much in the prompt:** models use information in the middle of long contexts poorly ("Lost in the Middle"). Fewer, better memories beat many.
- **Context bleed and over-personalisation:** memories surface where they don't belong. Willison's dog-in-a-pelican-costume picture gained an unasked-for "Half Moon Bay" sign.
- **Embellishment:** agents fill gaps with plausible invented details (Generative Agents 6.5.2).
- **Inflation and self-reinforcement:** importance that only goes up (the app's merge rule), and recall that refreshes itself.
- **Poisoning:** untrusted content writes memories (SpAIware).
- **User edits not sticking:** a secondary source reports Nomi Mind Map corrections reverting. *Unverified*, but it's a known class of bug whenever background consolidation can overwrite user edits.
- **Benchmarks don't settle it:** LoCoMo is short and flawed. Letta's filesystem result shows how much depends on the agent, not the store. You will need your own checks (5.6).

---

## 5. Recommendations for AI Harness

These are written so the scheme still works when the driving model is weak. Wherever possible the model makes **a categorical choice in words, with examples**, the app **derives** the rest, and a missing or garbled answer **falls back to a default**.

### 5.1 Importance

| Option | Pros | Cons |
|---|---|---|
| **A. Keep 1-10, model-chosen** | No change. Familiar from Generative Agents. Strong models use it reasonably. | No evidence it helps recall. Clusters and is inconsistent across models. False precision, since humans only agree about 23%. Only goes up on merge. Small effect in today's formula. |
| **B. Three named levels** (`minor` / `normal` / `major`) | Easy for any model. Matches how Nomi exposes priority. Loses little, given how small the effect is now. Easy to show and edit in the UI. | Coarser. Needs rationing of `major` or everything becomes major. |
| **C. Fully derived** (emotional intensity + use + provenance + kind) | No model judgement. Best-supported signals. | A persona can't say "this matters" about something that is calm and rarely mentioned but still matters. Usage signals can feed themselves. |
| **D. Drop it** (relevance + recency + pin only) | Simplest. Letta's result suggests search matters more than scoring. | Nothing to drive which memories resist fading, what goes dormant, or when to reflect. |

**My pick: B plus C.** The persona picks a level; the app derives everything else.

- Tool argument: `weight: "minor" | "normal" | "major"`, default `normal`, with anchors in the tool description:
  - `minor`: "a passing detail; fine if it fades (what they had for lunch)"
  - `normal`: "worth remembering"
  - `major`: "changes how I see them or us; I'd be upset to forget it (a loss, a confession, a big decision)"
  - Accept common synonyms and old integers: 1-3 become minor, 4-7 normal, 8-10 major.
- **Ration `major`:** for example, at most 20 per persona and user-persona pair. When over the limit, the heartbeat asks the persona which to demote. This is one categorical question, not scoring. Nomi caps "High" at 10.
- **Derived salience** for ranking (never asked of the model): `minor 0.2 / normal 0.5 / major 0.9`, **+0.1 if emotional intensity is strong**, **+0.1 if the user wrote, edited or confirmed it**, capped at 1.0. The user bonus is the "user-stated beats model-stated" signal from the multi-factor paper.
- **Merge rule:** on a near-duplicate merge, keep the existing level unless the new save says `major`. Then the heartbeat demotes stale majors, so levels can go down as well as up.
- `pinned`/CORE stays the separate "always in the system prompt" switch. Pinned means "must always know"; major means "matters deeply". They overlap but aren't the same thing.
- **Storage:** either an enum column, or keep the `importance` Int and store only 2, 5 or 9. The second avoids a schema change and keeps old data readable.

What salience then *does*:

1. A small ranking term, the same 0.20 slot as now.
2. Sets the decay half-life (5.3).
3. Exempts memories from going dormant.
4. Counts towards the reflection trigger (5.4).
5. Orders CORE memories in the prompt, as now.

### 5.2 Emotion

**Field shape (pick):**

```
feltMood:        String?   // the persona's mood word when the memory was kept; never changes
mood:            String?   // current view; starts equal to feltMood; may be revised
moodNote:        String?   // one sentence: why it changed (required when mood != feltMood)
moodRevisedAt:   Long?
moodRevisions:   Int       // count, for rate-limiting and freezing
```

Derived on read (not asked of the model):

- `valence`: positive, negative, mixed or neutral.
- `intensity`: mild or strong.

These come from a **small built-in table** of the mood words personas actually use (fond, wistful, anxious, elated, ...). The app's mood tags are free text of one or two words, so seed the table from observed tags. An unknown word gets classified **once**, categorically, by the model during a heartbeat ("is 'bittersweet' positive, negative or mixed? mild or strong?"), and the answer is cached app-wide. Until then it counts as neutral and mild.

I'd avoid the NRC Valence-Arousal-Dominance lexicon (20,000+ words) unless licensed: it is free for research, but commercial use needs permission from NRC.

**Defaults:**

- **memory_save during a reply:** `feltMood` = that reply's `[mood: ...]` tag. Let the tool accept an optional `mood` that overrides it, including `none`.
- **FACT kind:** record the mood but treat it as **display only**, with no intensity bonus, unless the persona explicitly gave one. A mundane fact saved while the persona happened to be elated shouldn't get a boost.
- **EPISODE:** peak-end rule. Use the strongest-intensity mood in the stretch, falling back to the last mood.
- **REFLECTION:** the heartbeat turn's own mood.
- **CORE/pinned:** optional.

**How reflection updates it:**

- In the heartbeat, the persona is shown a few memories it touched since the last heartbeat, with `felt` and `now`. It may call `memory_update(id, mood=..., reason=...)`.
- During normal turns, revision is allowed only for memories recalled in that turn. That's the reconsolidation window.
- Guards:
  - at most one revision per memory per 7 days;
  - freeze or flag after about 3 revisions;
  - a reason is required;
  - `feltMood` is never touched;
  - revisions are logged in the activity log;
  - the user can "reset to felt".
- **Prompt nudge:** "Feelings often soften as things resolve. Revise only if something has actually happened or you understand it differently now."

**Does it affect recall? (pick)**

1. **Yes, through intensity:** strong memories decay more slowly and get the +0.1 salience. This is the best-supported use.
2. **Display, yes:** recalled memories are rendered with their feeling, e.g. `- (felt anxious, now hopeful) Sam's interview at the library, March`. That helps the persona write with continuity.
3. **Mood-congruent boost: off at first.**
   - If tried: a small rerank among candidates that already passed `minRelevance` (semantic-first, as Emotional RAG and PsychoAgent do), e.g. +0.03 when the valence family matches the persona's current mood.
   - Not applied to negative moods, or applied only if at least one non-matching memory stays in the top-k. This avoids melancholy loops.
   - It makes a good A/B experiment rather than a default.

**Trade-offs:** two fields plus a note is a little more schema than one word. But it removes the main risk (rewriting history) and gives the persona a story of change. Deriving valence and intensity from a table is cruder than asking for numbers, but it is deterministic, free, and identical whichever model is driving.

### 5.3 Decay and forgetting

**Pick: half-life "strength from use", soft, with dormancy and supersession.**

- `strength = 0.5^(daysSinceLastUse / halfLife)`, with a floor of about 0.1, so decay reorders but never hides a strong match (Mem0's principle).
- `halfLife` by level: `minor 7 days`, `normal 30 days`, `major 180 days` (or no decay).
  - x2 if intensity is strong.
  - x1.5 for each *meaningful* use, capped at about 365 days.
  - This is MemoryBank's "recall strengthens" idea with salience-dependent starting points. It only needs `useCount` and `lastUsedAt`.
- **What counts as a meaningful use:**
  - the persona's own `memory_search` returned it;
  - a heartbeat reflection cited it;
  - the user edited or confirmed it;
  - optionally, the reply clearly drew on it (a cheap word-overlap check).
  - **Automatic top-k recall alone does not count.** It can update `lastAccessedAt` for display but doesn't extend the half-life. That ends the self-reinforcing loop.
- **Dormant:** not pinned, not major, strength below about 0.15, older than about 60 days. Dormant memories are skipped by automatic recall but stay available to `memory_search` and the memory screen. They are candidates for roll-up. **Never delete automatically**; deletion stays with `memory_forget` and the user.
- **Superseded:** add `supersededById` (and optionally `validUntil`). When a newer fact contradicts an older one, the persona (or the heartbeat) marks the old one superseded instead of overwriting it. Superseded memories are excluded from recall but remain as history ("lived in Leeds until 2026"). Optional `relevantUntil` covers plans and events (Mem0's expiration).
- **Alternative:** ACT-R activation over the last 10 to 20 use times. It is more principled and handles spacing, but it needs a small list per memory and is harder to explain in the UI. Worth it only if the simple version shows problems.

### 5.4 The heartbeat as consolidation

Each step is a small, structured job. Weak models can skip the optional ones.

1. **Fold:** turn new conversation into an EPISODE. This exists already. Add the felt mood (peak-end rule) and links to source messages.
2. **Reflect when emotional load builds up**, not only on a timer: for example, 3 or more new `major` or strong-intensity memories since the last reflection, or more than N days. This is Generative Agents' trigger without numbers. Reflections store `sourceIds`, the memories they cite.
3. **Reconsolidate:**
   - show 3 to 5 memories touched since last time, plus 1 or 2 dormant `major` ones as rehearsal;
   - the persona may revise mood (with a reason), change level, mark something superseded, or do nothing;
   - add a cooldown so the same old memory isn't resurfaced every day.
4. **Tidy:** merge near-duplicates, flag likely contradictions, enforce the `major` limit. Contradiction-spotting can be cheap at first: same entity plus a changed value, then confirmed by the persona.
5. **Roll up:** when more than about 20 dormant episodes accumulate for a relationship, write a "chapter" memory that summarises them and cites them. Keep the episodes as dormant sources.

Per Letta, consider an optional **separate "heartbeat model"** setting, so a stronger cloud model can tend memories even when a small local model handles conversation.

### 5.5 Robust across models

- No numeric judgements anywhere: levels, mood words and a few yes/no or pick-one questions.
- Tool descriptions carry examples. Parsers accept synonyms and old integers. Anything unrecognised falls back to `normal`, no revision, or neutral.
- One decision per call in the heartbeat ("which of these 3 should stay major?" rather than "re-score all memories").
- Derived signals carry the weight, so a weak model that ignores the levels entirely still gets sensible behaviour: everything is `normal`, and intensity and usage do the rest.

### 5.6 How to tell whether it works

Benchmarks won't answer this for a letters app. A small fixture harness on the existing in-memory storage would:

- script a few months of letters with planted facts, a change of fact (a move), a couple of emotional events, and some trivia;
- probe with questions (recall, current vs stale, "how did you feel about X then vs now?") and with "should anything be brought up?" moments;
- compare recall@k across variants: today's formula, three levels, with and without decay, with and without the mood-congruent tie-breaker;
- use LongMemEval's five abilities as a checklist: information extraction, multi-session reasoning, temporal reasoning, knowledge updates, and knowing when to say "I don't know".

Then do the owner's own read-through for tone. Does the persona bring up the right things, not too often?

---

## 6. Open questions for the owner

1. **What should "importance" mean?** "Matters emotionally to the persona" and "useful to know" are different, for example a bereavement vs an allergy. My pick treats the first as `major` and the second as `pinned`. Is that the split you want?
2. **Should emotion change *what* is recalled, or only *how* it's told?** I suggest intensity affects fading, display everywhere, and mood-matching off at first. Are you comfortable with personas whose current mood colours what they remember?
3. **Who has the last word on feelings?** Nomi doesn't let users edit memories; Kindroid lets users edit most things. Should the user be able to edit or reset a persona's `mood`, or only see it?
4. **How much forgetting do you want?** Visible fading of small details is more human. Never forgetting is more useful. Dormant-but-searchable is a middle path. Should the UI show dormant memories differently?
5. **Only the persona's emotion, or also the user's?** Recording the user's apparent mood is what most research systems do. It's also more sensitive, and the model can easily get it wrong.
6. **Should a revised emotion ever go *more* negative** without a new event? And should the persona mention a revision to the user ("I've been thinking about that letter, and I feel differently now")?
7. **Is a separate, stronger model for heartbeat tending acceptable** (cost, privacy) when conversation runs on a local model?
8. **How big should the `major` allowance be,** and should it be per user-persona or per persona?

---

## 7. Verification notes

- **Read in full or in the relevant sections** (primary sources): Generative Agents (paper and code), MemGPT, Sleep-time Compute, Mem0, A-MEM, MemoryBank, Zep, LongMemEval, Emotional RAG, LUFY, the multi-factor value preprint, the robot Kim preprint, the PsychoAgent abstract, G-Eval, Stureborg et al., the Lost in the Middle abstract. Also the Letta, Mem0, LangMem, Kindroid, Nomi-wiki and Character.AI pages, Zep's rebuttal, and Willison's post.
- **From search summaries only, not opened:**
  - OpenAI's memory pages (openai.com and help.openai.com were blocked), the October 2025 automatic-management details, and the Replika help page (blocked);
  - Character.AI's 2026 memory redesign, and the Nomi edit-revert report;
  - the human-memory papers apart from their abstracts and records (McGaugh, Kensinger & Corkin, Talarico & Rubin, Payne, Wilhelm, Walker & van der Helm, the fading-affect-bias reviews, Bower, Eich, Matt et al., Gaddy & Ingram, Nader et al., Levine & Safer, Kahneman et al., Roediger & Karpicke, Anderson & Schooler, ACT-R's decay of 0.5);
  - MemoryOS, Hou et al. (2024) and RMM: their mechanisms are known but not used for any claim above;
  - NRC lexicon licensing.
- **My own reading, unconfirmed:** the possible reversed recency ordering in the Generative Agents code.
- **Not found:** any published evaluation of Mem0's decay feature; any study that isolates the effect of a model-chosen importance score on recall; any system that stores the *agent's own* mood on its memories.

---

## Sources

**Agent memory systems (papers and code)**
- Park et al. (2023), *Generative Agents: Interactive Simulacra of Human Behavior*: https://arxiv.org/abs/2304.03442 . Code: https://github.com/joonspk-research/generative_agents (`reverie/backend_server/persona/cognitive_modules/retrieve.py`, `.../memory_structures/scratch.py`)
- Packer et al. (2023), *MemGPT: Towards LLMs as Operating Systems*: https://arxiv.org/abs/2310.08560
- Lin et al. (2025), *Sleep-time Compute: Beyond Inference Scaling at Test-time*: https://arxiv.org/abs/2504.13171
- Chhikara et al. (2025), *Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory*: https://arxiv.org/abs/2504.19413
- Xu et al. (2025), *A-MEM: Agentic Memory for LLM Agents*: https://arxiv.org/abs/2502.12110
- Zhong et al. (2023), *MemoryBank: Enhancing Large Language Models with Long-Term Memory*: https://arxiv.org/abs/2305.10250
- Rasmussen et al. (2025), *Zep: A Temporal Knowledge Graph Architecture for Agent Memory*: https://arxiv.org/abs/2501.13956
- Wu et al. (2025), *LongMemEval: Benchmarking Chat Assistants on Long-Term Interactive Memory* (ICLR 2025): https://arxiv.org/abs/2410.10813
- Maharana et al. (2024), *Evaluating Very Long-Term Conversational Memory of LLM Agents* (LoCoMo): https://arxiv.org/abs/2402.17753
- Huang et al. (2024), *Emotional RAG: Enhancing Role-Playing Agents through Emotional Retrieval*: https://arxiv.org/abs/2410.23041
- Sumida, Inoue & Kawahara (2025), *Enhancing Long-term RAG Chatbots with Psychological Models of Memory Importance and Forgetting* (LUFY), Dialogue & Discourse 16(2): https://arxiv.org/abs/2409.12524
- Chen & Cheng (2026, preprint), *Learning What to Remember: A Cognitively Grounded Multi-Factor Value Model for Agentic Memory*: https://arxiv.org/abs/2606.12945
- Aschenbrenner et al. (2026, preprint), *Not Forgotten: Implementation and Evaluation of a Personalized Episodic Memory for the Humanoid Robot Head Kim*: https://arxiv.org/abs/2607.24190
- Amanlou et al. (2026, preprint), *PsychoAgent: An Affect-Sensitive Cognitive Architecture for Conflict-Aware Memory in LLM Agents*: https://arxiv.org/abs/2608.07438
- Hou, Tamoto & Miyashita (2024), *"My agent understands me better"* (CHI EA): https://arxiv.org/abs/2404.00573 (not used for claims)
- Kang et al. (2025), *Memory OS of AI Agent*: https://arxiv.org/abs/2506.06326 (not used for claims)
- Tan et al. (2025), *In Prospect and Retrospect: Reflective Memory Management* (ACL): https://aclanthology.org/2025.acl-long.413 (not used for claims)
- Liu et al. (2023), *Lost in the Middle: How Language Models Use Long Contexts*: https://arxiv.org/abs/2307.03172

**LLM rating reliability**
- Liu et al. (2023), *G-Eval*: https://arxiv.org/abs/2303.16634
- Stureborg, Alikaniotis & Suhara (2024), *Large Language Models are Inconsistent and Biased Evaluators*: https://arxiv.org/abs/2405.01724

**Product documentation and engineering blogs**
- Letta, Memory blocks: https://docs.letta.com/v1-sdk/memory/memory-blocks
- Letta, *Sleep-time Compute* (blog): https://www.letta.com/blog/sleep-time-compute/
- Letta, *Benchmarking AI Agent Memory: Is a Filesystem All You Need?*: https://www.letta.com/blog/benchmarking-ai-agent-memory/
- Mem0, How it works: https://docs.mem0.ai/core-concepts/how-it-works
- Mem0, Memory Decay: https://docs.mem0.ai/platform/features/memory-decay
- Mem0, docs index (Memory Expiration and other features): https://docs.mem0.ai/llms.txt
- Zep, *Lies, Damn Lies, & Statistics: Is Mem0 Really SOTA in Agent Memory?*: https://www.getzep.com/blog/lies-damn-lies-statistics-is-mem0-really-sota-in-agent-memory/
- LangMem, Conceptual guide: https://langchain-ai.github.io/langmem/concepts/conceptual_guide/
- Kindroid Help Center, Memory: https://kindroid.ai/v2/docs/memory/
- Nomipedia: https://wiki.nomi.ai/How_does_Nomi_memory_and_knowledge_work%3F , https://wiki.nomi.ai/What_is_Identity_Core%3F , https://wiki.nomi.ai/The_Mind_Map_Priority_System , https://wiki.nomi.ai/Are_Mind_Map_Entries_Memories%3F , https://wiki.nomi.ai/Can_Editing_Mind_Maps_Hurt_A_Nomi_Or_Take_Away_Their_Agency
- Character.AI, *Helping Characters Remember What Matters Most*: https://blog.character.ai/helping-characters-remember-what-matters-most/
- Replika Help Center, *How does Replika's memory work* (search snippet only): https://help.replika.com/hc/en-us/articles/37208679176077-How-does-Replika-s-memory-work
- OpenAI, Memory FAQ (search snippet only): https://help.openai.com/en/articles/8590148-memory-faq ; *Memory and new controls for ChatGPT* (not accessible): https://openai.com/index/memory-and-new-controls-for-chatgpt/
- TechRadar on ChatGPT automatic memory management (search snippet only): https://www.techradar.com/ai-platforms-assistants/chatgpt/chatgpt-is-smarter-now-that-its-learned-to-forget-a-huge-memory-upgrade-is-coming
- Simon Willison, *I really don't like ChatGPT's new memory dossier* (May 2025): https://simonwillison.net/2025/May/21/chatgpt-new-memory/
- The Hacker News on Rehberger's SpAIware (search snippet only): https://thehackernews.com/2024/09/chatgpt-macos-flaw-couldve-enabled-long.html
- NRC VAD Lexicon, terms (search snippet only): https://www.saifmohammad.com/WebPages/nrc-vad.html

**Human memory research** (abstracts and records only)
- McGaugh (2004), *The amygdala modulates the consolidation of memories of emotionally arousing experiences*, Annu. Rev. Neurosci. 27: https://pubmed.ncbi.nlm.nih.gov/15217324/
- Kensinger & Corkin (2004), *Two routes to emotional memory*, PNAS 101(9): https://pmc.ncbi.nlm.nih.gov/articles/PMC365786
- Talarico & Rubin (2003), *Confidence, not consistency, characterizes flashbulb memories*, Psychol. Sci. 14(5): https://scholars.duke.edu/publication/651206
- Payne, Stickgold, Swanberg & Kensinger (2008), *Sleep preferentially enhances memory for emotional components of scenes*, Psychol. Sci. 19(8): https://pubmed.ncbi.nlm.nih.gov/18816285/
- Wilhelm et al. (2011), *Sleep selectively enhances memory expected to be of future relevance*, J. Neurosci. 31(5): https://pubmed.ncbi.nlm.nih.gov/21289163/
- Walker & van der Helm (2009), *Overnight therapy? The role of sleep in emotional brain processing*, Psychol. Bull. 135(5): https://walkerlab.berkeley.edu/reprints/WalkerVanDerHelm_PsychBull_2009.pdf
- Walker, Skowronski & Thompson (2003), *Life is pleasant, and memory helps to keep it that way!*, Rev. Gen. Psychol. 7(2) (APA press release): https://www.apa.org/news/press/releases/2003/06/happy-memory
- Bower (1981), *Mood and memory*, American Psychologist 36(2), 129-148 (cited via Emotional RAG; not opened): https://doi.org/10.1037/0003-066X.36.2.129
- Eich (1995), *Searching for mood dependent memory*, Psychol. Sci. 6(2): https://www.psychologicalscience.org/journals/psychological-science/j.1467-9280.1995.tb00309.x/
- Matt, Vázquez & Campbell (1992), *Mood-congruent recall of affectively toned stimuli: a meta-analytic review*, Clin. Psychol. Rev. 12, 227-255 (via secondary summaries; no link opened)
- Gaddy & Ingram (2014), *A meta-analytic review of mood-congruent implicit memory in depressed mood*: https://pubmed.ncbi.nlm.nih.gov/24980699/
- Nader, Schafe & LeDoux (2000), *Fear memories require protein synthesis in the amygdala for reconsolidation after retrieval*, Nature 406: https://pubmed.ncbi.nlm.nih.gov/10963596/
- Levine & Safer (2002), *Sources of bias in memory for emotions*, Curr. Dir. Psychol. Sci. 11(5): https://www.psychologicalscience.org/journals/current-directions/1467-8721.00193/
- Kahneman, Fredrickson, Schreiber & Redelmeier (1993), *When more pain is preferred to less: Adding a better end*, Psychol. Sci. 4(6): https://ius.uzh.ch/dam/jcr:5ae9adc9-61ec-4174-b37c-4b752f36c23b/Kahnemann%20et%20al.%20-%20When%20More%20Pain%20is%20Preferred%20to%20Less%20(1993).pdf
- Roediger & Karpicke (2006), *Test-enhanced learning*, Psychol. Sci. 17(3): https://psychology.ecu.edu/wp-content/pv-uploads/sites/216/2019/03/Roediger-Karpicke-2006.pdf
- Anderson & Schooler (1991), *Reflections of the environment in memory*, Psychol. Sci. 2(6): https://www.psychologicalscience.org/journals/psychological-science/j.1467-9280.1991.tb00174.x/
- Taatgen, Lebiere & Anderson, *Modeling paradigms in ACT-R* (base-level learning equation): https://www.ai.rug.nl/~niels/publications/taatgenLebiereAnderson.pdf
