import { z } from "zod";

export function pagedResponseSchema<TItem>(itemSchema: z.ZodType<TItem>) {
  return z.object({
    page: z.number().int().positive(),
    size: z.number().int().positive(),
    total: z.number().int().nonnegative(),
    items: z.array(itemSchema),
  });
}

export function countedResponseSchema<TItem>(itemSchema: z.ZodType<TItem>) {
  return z.object({
    count: z.number().int().nonnegative(),
    items: z.array(itemSchema),
  });
}
