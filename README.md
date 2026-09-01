# konaspeech.java

This repository holds the coding agent of the talk *Build a coding agent*, in Java. The agent is
one file, `Kona.java`. The talk builds it in eight chapters. Each chapter is a git tag.

| tag | it adds |
|---|---|
| `chapter-0` | the chat |
| `chapter-1` | `read_file`, and the loop |
| `chapter-2` | `list_files` |
| `chapter-3` | the reply guard, and one retry |
| `chapter-4` | `edit_file` |
| `chapter-5` | the unique-match check, the caps and the ceiling |
| `chapter-6` | the approval before a call leaves the project |
| `chapter-7` | one trace line for each iteration |

Each tag adds one thing to the tag before it. To see one chapter, compare it with the chapter
before it:

```sh
git diff chapter-1 chapter-2
```

## What you need

- Java 21 or later. `run.sh` uses `$JAVA_HOME/bin/java`. If `JAVA_HOME` is not set, it uses `/usr/bin/java`.
- [Ollama](https://ollama.com), on `localhost:11434`.
- The model `qwen3-coder:latest`. To get it, run `ollama pull qwen3-coder`.

The agent needs no API key and no network. The Jackson jars are in `lib/`.

## Run a chapter

1. Check out the chapter:

   ```sh
   git checkout chapter-3
   ```

2. Start the agent. Give it the folder of a project:

   ```sh
   ./run.sh /path/to/a/project
   ```

   The agent works in that folder. Without a folder, it works in the current folder.

3. The agent shows `You: `. Type a prompt, for example `what does this project do?`
4. To stop the agent, press Ctrl+D.

To go back to the last chapter, run `git checkout main`.

## The settings

| variable | what it does | the default |
|---|---|---|
| `KONA_MODEL` | selects the model | `qwen3-coder:latest` |
| `KONA_URL` | selects the endpoint | `http://localhost:11434/v1/chat/completions` |
| `KONA_TRACE` | from `chapter-7`: when set, it writes one JSON line for each iteration to `trace.jsonl` | not set |

Any endpoint that speaks the OpenAI chat completions protocol works.

## A crash that you can expect

`chapter-2` stops with a `ClassCastException` on some models, for example `gemma3`. This crash is
deliberate. `chapter-3` adds the reply guard that handles it. To see the crash, run:

```sh
git checkout chapter-2
KONA_MODEL=gemma3:latest ./run.sh /path/to/a/project
```

## The finished agent

[konacode](https://github.com/bbossola/konacode) is the full Java agent. This repository keeps
only the version of the talk.

## The Python version

[konaspeech.py](https://github.com/bbossola/konaspeech.py) holds the same agent in Python, with
the same chapters.

## The license

MIT. See [LICENSE](LICENSE).
