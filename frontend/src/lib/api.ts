import type {
  LessonDetailDto,
  LessonSummaryDto,
  PageDto,
  ProblemDetail,
  TextbookDto,
} from "./types";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(public readonly problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`);
    this.name = "ApiError";
  }
}

/** Calls the backend under `/api` and returns the parsed JSON body (undefined for 204). */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}/api${path}`, {
    ...init,
    headers: { Accept: "application/json", ...init?.headers },
  });

  if (!response.ok) {
    let problem: ProblemDetail = { status: response.status, title: response.statusText };
    if (response.headers.get("content-type")?.includes("json")) {
      problem = { ...problem, ...(await response.json()) };
    }
    throw new ApiError(problem);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// ------------------------------------------------------------------ textbooks & lessons

export const listTextbooks = () => apiFetch<TextbookDto[]>("/textbooks");

export const createTextbook = (body: { title: string; edition?: string }) =>
  apiFetch<TextbookDto>("/textbooks", jsonBody("POST", body));

export const listLessons = (textbookId: number) =>
  apiFetch<LessonSummaryDto[]>(`/textbooks/${textbookId}/lessons`);

export const createLesson = (textbookId: number, body: { lessonNo: number; title?: string }) =>
  apiFetch<LessonSummaryDto>(`/textbooks/${textbookId}/lessons`, jsonBody("POST", body));

export const getLesson = (id: number) => apiFetch<LessonDetailDto>(`/lessons/${id}`);

export const deleteLesson = (id: number) => apiFetch<void>(`/lessons/${id}`, { method: "DELETE" });

// ------------------------------------------------------------------ pages & extraction

export function uploadPage(lessonId: number, image: Blob, pageNo?: number): Promise<PageDto> {
  const form = new FormData();
  form.append("file", image, "page.jpg");
  if (pageNo !== undefined) form.append("pageNo", String(pageNo));
  return apiFetch<PageDto>(`/lessons/${lessonId}/pages`, { method: "POST", body: form });
}

export const getPage = (id: number) => apiFetch<PageDto>(`/pages/${id}`);

export const rerunExtraction = (id: number) => apiFetch<PageDto>(`/pages/${id}/extract`, { method: "POST" });

export const deletePage = (id: number) => apiFetch<void>(`/pages/${id}`, { method: "DELETE" });

export const pageImageUrl = (id: number) => `${API_BASE_URL}/api/pages/${id}/image`;

function jsonBody(method: string, body: unknown): RequestInit {
  return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}
