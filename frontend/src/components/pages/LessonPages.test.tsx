import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as api from "@/lib/api";
import * as image from "@/lib/image";
import { EXTRACTION, page } from "@/test/fixtures";
import LessonPages, { POLL_INTERVAL_MS } from "./LessonPages";

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  getPage: vi.fn(),
  uploadPage: vi.fn(),
  rerunExtraction: vi.fn(),
  deletePage: vi.fn(),
}));
vi.mock("@/lib/image", async (original) => ({
  ...(await original<typeof import("@/lib/image")>()),
  prepareImage: vi.fn(),
}));

describe("LessonPages", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("polls unfinished pages until extraction is done", async () => {
    vi.mocked(api.getPage)
      .mockResolvedValueOnce(page({ status: "RUNNING" }))
      .mockResolvedValueOnce(page({ status: "DONE", extraction: EXTRACTION }));
    render(<LessonPages lessonId={7} initialPages={[page()]} />);
    expect(screen.getByText("Waiting")).toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS));
    expect(screen.getByText("Reading page…")).toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS));
    expect(screen.getByText("Done")).toBeInTheDocument();
    expect(screen.getByText(/2 words · vocabulary list · 1 warning/)).toBeInTheDocument();
    // the ideographic space between words is normalised to " " by Testing Library
    expect(screen.getByText("朋友 老师")).toHaveAttribute("lang", "zh-CN");

    await act(() => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3));
    expect(api.getPage).toHaveBeenCalledTimes(2);
  });

  it("keeps polling the other pages when one fails, and drops pages deleted elsewhere", async () => {
    vi.mocked(api.getPage).mockImplementation(async (id) => {
      if (id === 1) throw new api.ApiError({ status: 404, detail: "Page 1 not found" });
      if (id === 2) throw new TypeError("fetch failed");
      return page({ id, pageNo: id, status: "DONE", extraction: EXTRACTION });
    });
    render(
      <LessonPages
        lessonId={7}
        initialPages={[page({ id: 1, pageNo: 1 }), page({ id: 2, pageNo: 2 }), page({ id: 3, pageNo: 3 })]}
      />,
    );

    await act(() => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS));

    expect(screen.queryByRole("listitem", { name: "Page 1" })).not.toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: "Page 3" })).toHaveTextContent("Done");
    expect(screen.getByRole("listitem", { name: "Page 2" })).toHaveTextContent("Waiting");
    expect(screen.getByText("Cannot reach the backend. Is it running?")).toBeInTheDocument();

    vi.mocked(api.getPage).mockResolvedValue(page({ id: 2, pageNo: 2, status: "DONE", extraction: EXTRACTION }));
    await act(() => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS));

    expect(screen.getByRole("listitem", { name: "Page 2" })).toHaveTextContent("Done");
    expect(screen.queryByText("Cannot reach the backend. Is it running?")).not.toBeInTheDocument();
  });

  it("does not poll when every page is finished", async () => {
    render(<LessonPages lessonId={7} initialPages={[page({ status: "DONE", extraction: EXTRACTION })]} />);

    await act(() => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3));

    expect(api.getPage).not.toHaveBeenCalled();
  });

  it("uploads chosen files with consecutive page numbers", async () => {
    vi.mocked(image.prepareImage).mockResolvedValue(new Blob(["jpeg"]));
    vi.mocked(api.uploadPage)
      .mockResolvedValueOnce(page({ id: 1, pageNo: 4 }))
      .mockResolvedValueOnce(page({ id: 2, pageNo: 5 }));
    vi.mocked(api.getPage).mockImplementation(async (id) => page({ id, pageNo: id + 3 }));
    render(<LessonPages lessonId={7} initialPages={[]} />);

    fireEvent.change(screen.getByLabelText("First page no."), { target: { value: "4" } });
    const files = [new File(["a"], "a.heic"), new File(["b"], "b.jpg")];
    await act(async () => {
      fireEvent.change(screen.getByTestId("page-file-input"), { target: { files } });
    });

    expect(api.uploadPage).toHaveBeenNthCalledWith(1, 7, expect.any(Blob), 4);
    expect(api.uploadPage).toHaveBeenNthCalledWith(2, 7, expect.any(Blob), 5);
    expect(screen.getByRole("listitem", { name: "Page 4" })).toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: "Page 5" })).toBeInTheDocument();
    expect(screen.getByLabelText("First page no.")).toHaveValue(6);
  });

  it("reports files that cannot be prepared and keeps going", async () => {
    vi.mocked(image.prepareImage)
      .mockRejectedValueOnce(new Error("This image could not be read. Please upload a JPEG or PNG photo."))
      .mockResolvedValueOnce(new Blob(["jpeg"]));
    vi.mocked(api.uploadPage).mockResolvedValueOnce(page({ id: 2, pageNo: null }));
    vi.mocked(api.getPage).mockResolvedValue(page({ id: 2, pageNo: null }));
    render(<LessonPages lessonId={7} initialPages={[]} />);

    await act(async () => {
      fireEvent.change(screen.getByTestId("page-file-input"), {
        target: { files: [new File(["?"], "scan.tiff"), new File(["b"], "b.jpg")] },
      });
    });

    expect(screen.getByRole("alert")).toHaveTextContent("scan.tiff: This image could not be read.");
    expect(screen.getByRole("listitem", { name: "Page #2" })).toBeInTheDocument();
  });

  it("re-runs a failed page and deletes after confirmation", async () => {
    vi.mocked(api.rerunExtraction).mockResolvedValue(page({ id: 1, status: "PENDING" }));
    vi.mocked(api.deletePage).mockResolvedValue(undefined);
    render(
      <LessonPages
        lessonId={7}
        initialPages={[page({ id: 1, status: "FAILED", errorMessage: "Extraction timed out after 120 s" })]}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Extraction timed out after 120 s");

    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Re-run extraction" })));
    expect(api.rerunExtraction).toHaveBeenCalledWith(1);
    expect(screen.getByText("Waiting")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Delete page" }));
    expect(api.deletePage).not.toHaveBeenCalled();
    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Confirm delete" })));
    expect(api.deletePage).toHaveBeenCalledWith(1);
    expect(screen.getByText(/No pages yet/)).toBeInTheDocument();
  });

  it("shows backend errors such as 503 when the LLM is not configured", async () => {
    vi.mocked(api.rerunExtraction).mockRejectedValue(
      new api.ApiError({ status: 503, detail: "LLM provider 'anthropic' needs ANTHROPIC_API_KEY" }),
    );
    render(<LessonPages lessonId={7} initialPages={[page({ status: "FAILED" })]} />);

    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Re-run extraction" })));

    expect(screen.getByText(/needs ANTHROPIC_API_KEY/)).toHaveAttribute("role", "alert");
  });
});
