import Link from "next/link";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import DeleteLessonButton from "@/components/lessons/DeleteLessonButton";
import LessonPages from "@/components/pages/LessonPages";
import { ApiError, getLesson } from "@/lib/api";

export default async function LessonPage({ params }: { params: Promise<{ id: string }> }) {
  await connection();
  const { id } = await params;
  const lesson = await getLesson(Number(id)).catch((e: unknown) => {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  });

  return (
    <section className="flex flex-col gap-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link href="/lessons" className="text-sm text-muted hover:text-foreground">
            ← Lessons
          </Link>
          <h1 className="mt-1 text-2xl font-semibold">
            Lesson {lesson.lessonNo}
            {lesson.title && (
              <span lang="zh-CN" className="ml-2">
                {lesson.title}
              </span>
            )}
          </h1>
        </div>
        <DeleteLessonButton lessonId={lesson.id} />
      </div>

      <LessonPages lessonId={lesson.id} initialPages={lesson.pages} />

      <section className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">
          Words <span className="text-sm font-normal text-muted">({lesson.words.length})</span>
        </h2>
        {lesson.words.length === 0 ? (
          <p className="text-muted">No confirmed words yet. Words appear here after you review a page.</p>
        ) : (
          <ul className="grid gap-x-6 gap-y-1 sm:grid-cols-2">
            {lesson.words.map((w) => (
              <li key={w.id} className="flex items-baseline gap-3">
                <span lang="zh-CN" className="text-lg">
                  {w.hanzi}
                </span>
                <span>{w.pinyin}</span>
                <span className="text-muted">{w.meaningDe ?? w.meaningEn}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  );
}
