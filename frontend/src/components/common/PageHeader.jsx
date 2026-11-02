export default function PageHeader({ title, subtitle, actions }) {
  return (
    <header className="page-heading">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div>{actions}</div>}
    </header>
  );
}
