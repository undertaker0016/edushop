export function paymentReference(value) {
  return /^(?:PAY-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9]{6}_[0-9a-f]{32})$/i.test(
    value || "",
  )
    ? value
    : null;
}
export function checkoutUrl(value, provider) {
  const host =
    provider === "MOMO_SANDBOX"
      ? "test-payment.momo.vn"
      : provider === "MOMO_PRODUCTION"
        ? "payment.momo.vn"
        : provider === "ZALOPAY_SANDBOX"
          ? "qcgateway.zalopay.vn"
          : null;
  try {
    const url = new URL(value);
    if (
      !host ||
      url.protocol !== "https:" ||
      (url.hostname !== host &&
        !(
          provider === "ZALOPAY_SANDBOX" &&
          url.hostname === "sbgateway.zalopay.vn"
        )) ||
      url.username ||
      url.password ||
      (url.port && url.port !== "443")
    )
      throw new Error();
    return url.href;
  } catch {
    throw new Error("Đường dẫn thanh toán không thuộc cổng đã cấu hình.");
  }
}
export function providerLabel(provider) {
  return provider === "ZALOPAY_SANDBOX"
    ? "ZaloPay"
    : provider?.startsWith("MOMO_")
      ? "MoMo"
      : "cổng thanh toán";
}
