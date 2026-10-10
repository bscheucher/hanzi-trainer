import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch, uploadPage } from "./api";

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

describe("uploadPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("posts multipart with the file and optional page number", async () => {
    const fetchMock = mockFetch(Response.json({ id: 3, status: "PENDING" }, { status: 202 }));

    await uploadPage(7, new Blob(["x"], { type: "image/jpeg" }), 12);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/lessons/7/pages");
    expect(init.method).toBe("POST");
    const form = init.body as FormData;
    expect(form.get("file")).toBeInstanceOf(Blob);
    expect(form.get("pageNo")).toBe("12");
    expect(init.headers).not.toHaveProperty("Content-Type"); // browser sets the multipart boundary
  });

  it("omits the page number when not given", async () => {
    const fetchMock = mockFetch(Response.json({ id: 3 }, { status: 202 }));

    await uploadPage(7, new Blob(["x"]));

    expect((fetchMock.mock.calls[0][1].body as FormData).has("pageNo")).toBe(false);
  });
});
