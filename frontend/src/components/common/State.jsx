import { LoadingSkeleton, EmptyState, ErrorState } from "../ui";
export { ErrorState };
export function Loading() {
  return <LoadingSkeleton />;
}
export function Empty(props) {
  return <EmptyState {...props} />;
}
