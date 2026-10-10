import type { ProblemDetail } from "./types";

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
