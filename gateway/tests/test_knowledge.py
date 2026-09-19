"""
Mirrors app/src/test/java/com/sankatsetu/app/assistant/{KnowledgeRetrieverTest,
KnowledgeDocumentParserTest}.kt case-by-case, so knowledge.py's port of the
Kotlin retriever/parser is verified against the same behavior the on-device
engine's own test suite already locks in — not just "it runs," but "it
agrees with the Kotlin original on these exact cases." Pure stdlib
(`unittest`), no dependency install needed:

    python -m unittest gateway.tests.test_knowledge -v
"""
from __future__ import annotations

import unittest

from gateway.agent.knowledge import KnowledgeChunk, parse_document, search, tokenize


class KnowledgeRetrieverTest(unittest.TestCase):
    def setUp(self) -> None:
        self.chunks = [
            KnowledgeChunk(
                "bleeding", "Test Source A", "Bleeding",
                "Apply firm direct pressure to a bleeding wound with a clean cloth.",
            ),
            KnowledgeChunk(
                "snakebite", "Test Source B", "Snake bite",
                "Keep the person still after a snake bite and get to a hospital immediately. "
                "Do not cut the snake bite wound or try to suck out venom.",
            ),
            KnowledgeChunk(
                "flood", "Test Source C", "Flood",
                "Move to higher ground during a flood and avoid walking through moving flood water.",
            ),
        ]

    def test_query_strongly_matching_one_chunks_distinctive_terms_ranks_it_first(self):
        results = search("what do I do about a snake bite", self.chunks)
        self.assertTrue(results)
        self.assertEqual("snakebite", results[0].chunk.chunk_id)

    def test_a_term_repeated_in_only_one_chunk_outranks_a_single_shared_word_match(self):
        results = search("bleeding", self.chunks)
        self.assertEqual("bleeding", results[0].chunk.chunk_id)

    def test_query_with_no_relevant_terms_returns_nothing(self):
        results = search("what is the best pizza topping", self.chunks)
        self.assertEqual([], results)

    def test_stop_words_alone_never_match_anything(self):
        results = search("what is the and a", self.chunks)
        self.assertEqual([], results)

    def test_top_k_limits_the_number_of_returned_matches(self):
        results = search("water wound bite", self.chunks, top_k=2)
        self.assertLessEqual(len(results), 2)

    def test_results_are_sorted_highest_score_first(self):
        results = search("flood water bite wound", self.chunks, top_k=3)
        scores = [r.score for r in results]
        self.assertEqual(sorted(scores, reverse=True), scores)

    def test_tokenize_lowercases_and_strips_punctuation(self):
        tokens = tokenize("Snake-Bite! What now?")
        self.assertIn("snake", tokens)
        self.assertIn("bite", tokens)
        self.assertTrue(all("!" not in t and "-" not in t for t in tokens))


class KnowledgeDocumentParserTest(unittest.TestCase):
    def test_parses_title_source_and_sections_into_separate_chunks(self):
        doc = (
            "# Snake Bite Response\n"
            "Source: WHO snakebite envenoming guidance (authored summary)\n"
            "\n"
            "## Immediate Steps\n"
            "Keep the person calm and still. Remove tight clothing or jewellery near the bite.\n"
            "\n"
            "## What Not To Do\n"
            "Do not cut the wound, apply a tourniquet, or attempt to suck out venom.\n"
        )
        chunks = parse_document("snake_bite", doc)

        self.assertEqual(2, len(chunks))
        self.assertEqual("Snake Bite Response", chunks[0].source)
        self.assertEqual("Immediate Steps", chunks[0].section)
        self.assertIn("calm and still", chunks[0].text)
        self.assertEqual("What Not To Do", chunks[1].section)
        self.assertIn("tourniquet", chunks[1].text)

    def test_missing_title_falls_back_to_the_file_name_as_source(self):
        doc = "## Only Section\nSome body text here.\n"
        chunks = parse_document("untitled_doc", doc)

        self.assertEqual(1, len(chunks))
        self.assertEqual("untitled_doc", chunks[0].source)

    def test_empty_sections_produce_no_chunk(self):
        doc = "# Title\n\n## Empty Section\n\n## Real Section\nActual content.\n"
        chunks = parse_document("doc", doc)

        self.assertEqual(1, len(chunks))
        self.assertEqual("Real Section", chunks[0].section)

    def test_chunk_ids_are_unique_and_stable_within_a_document(self):
        doc = "# Title\n\n## First\na\n\n## Second\nb\n"
        chunks = parse_document("doc", doc)

        self.assertEqual(["doc#0", "doc#1"], [c.chunk_id for c in chunks])


if __name__ == "__main__":
    unittest.main()
