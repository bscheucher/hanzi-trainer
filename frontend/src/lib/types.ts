// Mirrors the DTOs in src/docs/specs/03-api.md.

/** RFC 7807 error body returned by the backend for every error. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
}
