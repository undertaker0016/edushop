const BASE = (
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1"
).replace(/\/$/, "");

export class ApiError extends Error {
  constructor(message, status, problem) {
    super(message);
    this.status = status;
    this.problem = problem;
  }
}

function localizeError(message, status) {
  const value = String(message || "").trim();
  const known = [
    [
      /Invalid email or password|Bad credentials/i,
      "Email hoặc mật khẩu không đúng.",
    ],
    [
      /Invalid page or size|Invalid filters/i,
      "Bộ lọc hoặc số trang không hợp lệ.",
    ],
    [
      /Invalid page, size or time range/i,
      "Khoảng thời gian hoặc trang đã chọn không hợp lệ.",
    ],
    [/Search filters are too long/i, "Từ khóa tìm kiếm quá dài."],
    [
      /Course not found|Published course not found/i,
      "Không tìm thấy khóa học hoặc khóa học không còn được mở.",
    ],
    [
      /Offering not found|Offering not available/i,
      "Không tìm thấy lớp hoặc lớp không còn mở đăng ký.",
    ],
    [/User not found|Account not found/i, "Không tìm thấy tài khoản."],
    [/Instructor not found/i, "Không tìm thấy hồ sơ giảng viên."],
    [/Review not found/i, "Không tìm thấy đánh giá."],
    [
      /Authentication is required|Invalid or expired bearer token/i,
      "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
    ],
    [/Access is denied/i, "Bạn không có quyền thực hiện thao tác này."],
    [
      /Request validation failed|Request parameters or body are invalid/i,
      "Thông tin gửi lên chưa hợp lệ. Vui lòng kiểm tra các trường và thử lại.",
    ],
    [
      /Request conflicts with existing data/i,
      "Dữ liệu đang được sử dụng hoặc bị trùng. Vui lòng tải lại và kiểm tra.",
    ],
    [
      /No payment provider is configured|Payment provider is not configured/i,
      "Chưa cấu hình cổng thanh toán.",
    ],
    [
      /Only draft courses can be published/i,
      "Chỉ có thể xuất bản khóa học ở trạng thái bản nháp.",
    ],
    [
      /Course slug already exists|Category slug already exists|Tag already exists/i,
      "Tên hoặc đường dẫn đã được sử dụng.",
    ],
    [
      /Capacity cannot be lower than booked/i,
      "Sĩ số không thể thấp hơn số chỗ đã được giữ hoặc thanh toán.",
    ],
    [
      /Schedule cannot be changed after payment/i,
      "Không thể đổi lịch của lớp đã có đơn thanh toán.",
    ],
    [
      /An offering with reservations or paid orders cannot be cancelled/i,
      "Không thể hủy lớp đã có chỗ được giữ hoặc đơn thanh toán.",
    ],
    [/Registration deadline has passed/i, "Đã quá hạn đăng ký lớp."],
    [
      /Add at least one session before opening/i,
      "Hãy thêm ít nhất một buổi học trước khi mở bán.",
    ],
  ];
  for (const [pattern, translation] of known)
    if (pattern.test(value)) return translation;
  if (/[À-ỹ]/.test(value)) return value;
  if (status === 401) return "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.";
  if (status === 403) return "Bạn không có quyền thực hiện thao tác này.";
  if (status === 404) return "Không tìm thấy dữ liệu yêu cầu.";
  if (status === 409)
    return "Thao tác không phù hợp với trạng thái dữ liệu hiện tại.";
  if (status === 429)
    return "Bạn thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại.";
  if (status >= 500) return "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.";
  if (status === 400)
    return "Thông tin gửi lên chưa hợp lệ. Vui lòng kiểm tra và thử lại.";
  return "Thao tác chưa hoàn tất. Vui lòng thử lại.";
}

function localizeValidation(message) {
  const value = String(message || "");
  if (/must not be blank|must not be empty/i.test(value))
    return "không được để trống.";
  if (/must be a well-formed email address/i.test(value))
    return "phải là địa chỉ email hợp lệ.";
  if (/must match/i.test(value)) return "chưa đúng định dạng.";
  if (/must be greater than or equal to 1/i.test(value))
    return "phải lớn hơn hoặc bằng 1.";
  if (/must be greater than or equal to 0/i.test(value))
    return "không được nhỏ hơn 0.";
  if (/must be less than or equal to 100/i.test(value))
    return "không được vượt quá 100.";
  if (/size must be between/i.test(value)) return "có độ dài chưa phù hợp.";
  if (/[À-ỹ]/.test(value)) return value;
  return "chưa hợp lệ.";
}

async function request(
  path,
  { method = "GET", body, token, headers = {} } = {},
) {
  let response;
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body === undefined || body instanceof FormData
          ? {}
          : { "Content-Type": "application/json" }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body:
        body === undefined
          ? undefined
          : body instanceof FormData
            ? body
            : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(
      "Không kết nối được máy chủ. Vui lòng kiểm tra kết nối mạng và thử lại.",
      0,
      null,
    );
  }
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text || null;
  }
  if (!response.ok) {
    if (response.status === 401 && token) {
      window.dispatchEvent(new Event("edushop:unauthorized"));
    }
    const names = {
      fullName: "Họ và tên",
      email: "Email",
      password: "Mật khẩu",
      phone: "Số điện thoại",
    };
    if (data?.errors) {
      const message = Object.entries(data.errors)
        .map(([field, message]) =>
          field === "password"
            ? "Mật khẩu phải có từ 12 đến 72 ký tự."
            : `${names[field] || field} ${localizeValidation(message)}`,
        )
        .join(" ");
      throw new ApiError(message, response.status, data);
    }
    throw new ApiError(
      localizeError(data?.detail || data?.title, response.status),
      response.status,
      data,
    );
  }
  return data;
}

export const api = {
  get: (path, token) => request(path, { token }),
  post: (path, body, token, headers) =>
    request(path, { method: "POST", body, token, headers }),
  put: (path, body, token) => request(path, { method: "PUT", body, token }),
  patch: (path, body, token) => request(path, { method: "PATCH", body, token }),
  del: (path, token) => request(path, { method: "DELETE", token }),
};

export function mediaUrl(value) {
  if (!value) return "";
  if (value.startsWith("/api/v1/media/")) return new URL(BASE).origin + value;
  return value;
}
