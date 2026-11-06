export type ApiFieldError = {
  field: string;
  reason: string;
};

export type ApiErrorBody = {
  code: string;
  message: string;
  fields?: ApiFieldError[];
};

export type ApiResponse<T> =
  { success: true; data: T } | { success: false; error: ApiErrorBody };
