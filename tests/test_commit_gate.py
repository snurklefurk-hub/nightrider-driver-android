"""Keep the owner-approved NightRider commit questions and enforcement synchronized."""
from pathlib import Path
from tempfile import TemporaryDirectory
import os
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
QUESTIONS = (
    "Does it make delivering a professional ride easier or safer?",
    "Does it make the rider's experience more dependable, transparent, or trustworthy?",
    "Does it make the industry better?",
)


class CommitGatePolicyTests(unittest.TestCase):
    def test_policy_sources_share_questions_and_order(self):
        for relative in (
            "AGENTS.md", "README.md", ".github/PULL_REQUEST_TEMPLATE.md",
            ".github/workflows/commit-value-gate.yml", ".gitmessage", ".githooks/commit-msg"
        ):
            source = (ROOT / relative).read_text(encoding="utf-8")
            positions = [source.find(question) for question in QUESTIONS]
            self.assertTrue(all(position >= 0 for position in positions), relative)
            self.assertEqual(positions, sorted(positions), relative)
            self.assertNotIn(
                "Does it improve the driver's profitability, independence, or ability to retain customers?",
                source, relative
            )
        workflow = (ROOT / ".github/workflows/commit-value-gate.yml").read_text(encoding="utf-8")
        self.assertIn("safety|customer-trust|industry", workflow)
        self.assertNotIn("safety|driver-business|customer-trust", workflow)

    @unittest.skipIf(os.name == "nt" and shutil.which("sh") is None,
                     "POSIX shell is unavailable on this Windows installation")
    def test_local_commit_hook_validates_current_categories(self):
        hook = ROOT / ".githooks/commit-msg"
        subprocess.run(["sh", "-n", str(hook)], check=True, capture_output=True)
        examples = (
            ("NR-Value: safety - Keeps pickup guidance stable under weak GPS fixes.", True),
            ("NR-Value: customer-trust - Explains unexpected pickup delays to riders.", True),
            ("NR-Value: industry - Makes local transportation practices more transparent.", True),
            ("NR-Override: MAKE IT SO - Apply the owner's approved scoped experiment.", True),
            ("NR-Value: driver-business - An obsolete category is not a current test.", False),
            ("NR-Value: industry - vague", False),
            ("NR-Override: MAKE IT SO", False),
        )
        with TemporaryDirectory() as folder:
            message_file = Path(folder) / "commit.txt"
            for message, allowed in examples:
                message_file.write_text("Test commit\n\n" + message + "\n", encoding="utf-8")
                result = subprocess.run(["sh", str(hook), str(message_file)],
                                        capture_output=True, text=True, check=False)
                self.assertEqual(result.returncode == 0, allowed, message + result.stderr)


if __name__ == "__main__":
    unittest.main()
