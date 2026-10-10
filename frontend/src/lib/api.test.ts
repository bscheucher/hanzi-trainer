import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch } from "./api";

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("apiFetch", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("prefixes /api and returns the JSON body", async () => {
    const fetchMock = mockFetch(Response.json([{ id: 1 }]));

    await expect(apiFetch("/textbooks")).resolves.toEqual([{ id: 1 }]);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/textbooks", expect.anything());
  });

  it("returns undefined for 204", async () => {
    mockFetch(new Response(null, { status: 204 }));

    await expect(apiFetch("/lessons/1", { method: "DELETE" })).resolves.toBeUndefined();
  });

  it("throws ApiError carrying the ProblemDetail", async () => {
    mockFetch(
      new Response(JSON.stringify({ status: 404, title: "Not found", detail: "Lesson 42 not found" }), {
        status: 404,
        headers: { "content-type": "application/problem+json" },
      }),
    );

    const error = await apiFetch("/lessons/42").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).problem.status).toBe(404);
    expect((error as ApiError).message).toBe("Lesson 42 not found");
  });
});
