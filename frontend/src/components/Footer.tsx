export default function Footer() {
  return (
    <footer className="border-t border-border">
      <p className="mx-auto max-w-5xl px-4 py-3 text-xs text-muted">
        Dictionary data:{" "}
        <a
          href="https://www.mdbg.net/chinese/dictionary?page=cc-cedict"
          className="underline hover:text-foreground"
        >
          CC-CEDICT
        </a>
        ,{" "}
        <a
          href="https://creativecommons.org/licenses/by-sa/4.0/"
          className="underline hover:text-foreground"
        >
          CC BY-SA 4.0
        </a>
      </p>
    </footer>
  );
}
