/** Long edge sent to the backend; keep in sync with `hanzi.extraction.max-image-px` (spec 05). */
export const MAX_IMAGE_PX = 2576;

export function fitWithin(width: number, height: number, max: number) {
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.round(width * scale), height: Math.round(height * scale) };
}

/**
 * Decodes a photo (applying its EXIF orientation), downscales it and re-encodes it as JPEG, so phones' HEIC
 * photos and sideways shots arrive upright and small (spec 05, upload flow step 2).
 */
export async function prepareImage(file: Blob, maxPx = MAX_IMAGE_PX): Promise<Blob> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new Error("This image could not be read. Please upload a JPEG or PNG photo.");
  }
  const { width, height } = fitWithin(bitmap.width, bitmap.height, maxPx);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Your browser cannot process images.");
  context.fillStyle = "#fff";
  context.fillRect(0, 0, width, height);
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("Encoding the image failed."))),
      "image/jpeg",
      0.9,
    ),
  );
}
