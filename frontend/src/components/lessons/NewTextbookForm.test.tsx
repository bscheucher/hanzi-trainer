import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as api from "@/lib/api";
import NewLessonForm from "./NewLessonForm";
import NewTextbookForm from "./NewTextbookForm";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  createTextbook: vi.fn(),
  createLesson: vi.fn(),
}));

afterEach(() => vi.clearAllMocks());

describe("NewTextbookForm", () => {
  it("creates a textbook and refreshes the list", async () => {
    vi.mocked(api.createTextbook).mockResolvedValue({ id: 1, title: "HSK 1", edition: null, lessonCount: 0 });
    render(<NewTextbookForm />);

    fireEvent.change(screen.getByLabelText("Title"), { target: { value: " HSK 1 " } });
    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Add textbook" })));

    expect(api.createTextbook).toHaveBeenCalledWith({ title: "HSK 1", edition: undefined });
    expect(refresh).toHaveBeenCalled();
    expect(screen.getByLabelText("Title")).toHaveValue("");
  });
});

describe("NewLessonForm", () => {
  it("suggests the next lesson number and shows conflicts", async () => {
    vi.mocked(api.createLesson).mockRejectedValue(
      new api.ApiError({ status: 409, detail: "Lesson 3 already exists in this textbook" }),
    );
    render(<NewLessonForm textbookId={5} nextLessonNo={3} />);
    expect(screen.getByLabelText("Lesson no.")).toHaveValue(3);

    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Add lesson" })));

    expect(api.createLesson).toHaveBeenCalledWith(5, { lessonNo: 3, title: undefined });
    expect(screen.getByRole("alert")).toHaveTextContent("Lesson 3 already exists");
    expect(refresh).not.toHaveBeenCalled();
  });
});
