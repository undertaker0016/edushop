import PageHeader from "./PageHeader";

export default function Shell({ title, subtitle, actions, children }) {
  return (
    <main className="page">
      <PageHeader title={title} subtitle={subtitle} actions={actions} />
      {children}
    </main>
  );
}
