import Link from "next/link";
import { connection } from "next/server";
import NewLessonForm from "@/components/lessons/NewLessonForm";
import NewTextbookForm from "@/components/lessons/NewTextbookForm";
import { listLessons, listTextbooks } from "@/lib/api";

export default async function LessonsPage() {
  await connection(); // data comes from the backend at request time
  const textbooks = await listTextbooks();
  const lessons = await Promise.all(textbooks.map((t) => listLessons(t.id)));

  return (
    <section className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold">Lessons</h1>
        <p className="mt-1 text-muted">Add your textbook, then a lesson, then upload photos of its pages.</p>
      </div>

      {textbooks.map((textbook, i) => (
        <article key={textbook.id} className="flex flex-col gap-3">
          <h2 className="text-xl font-semibold">
            {textbook.title}
            {textbook.edition && <span className="ml-2 text-sm font-normal text-muted">{textbook.edition}</span>}
          </h2>
          {lessons[i].length > 0 && (
            <ul className="divide-y divide-border rounded border border-border">
              {lessons[i].map((lesson) => (
                <li key={lesson.id}>
                  <Link
                    href={`/lessons/${lesson.id}`}
                    className="flex items-baseline justify-between gap-4 px-3 py-2 hover:bg-foreground/5"
                  >
                    <span>
                      Lesson {lesson.lessonNo}
                      {lesson.title && (
                        <span lang="zh-CN" className="ml-2">
                          {lesson.title}
                        </span>
                      )}
                    </span>
                    <span className="text-sm text-muted">
                      {plural(lesson.pageCount, "page")} · {plural(lesson.wordCount, "word")}
                      {lesson.pendingReviewPages > 0 && (
                        <span className="ml-2 text-accent">{lesson.pendingReviewPages} to review</span>
                      )}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <NewLessonForm
            textbookId={textbook.id}
            nextLessonNo={Math.max(0, ...lessons[i].map((l) => l.lessonNo)) + 1}
          />
        </article>
      ))}

      <div className="border-t border-border pt-6">
        <h2 className="mb-3 text-lg font-semibold">{textbooks.length === 0 ? "Add your first textbook" : "Add a textbook"}</h2>
        <NewTextbookForm />
      </div>
    </section>
  );
}

function plural(count: number, noun: string) {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}
