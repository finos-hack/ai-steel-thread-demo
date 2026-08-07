import { finosProjects } from './finosProjects'

export function FinosProjectsPanel() {
  return (
    <aside className="finos-panel" aria-label="FINOS projects">
      <p className="sample-preview-label">FINOS projects</p>
      <h2 className="finos-panel-title">Built on the FINOS stack</h2>
      <p className="finos-panel-lede">
        This steel-thread demo connects open FINOS projects for AI governance, cloud controls,
        architecture-as-code, and process orchestration.
      </p>
      <ul className="finos-project-list">
        {finosProjects.map((project) => (
          <li key={project.id}>
            <a
              className="finos-project"
              href={project.href}
              target="_blank"
              rel="noreferrer"
            >
              <img
                className="finos-project-logo"
                src={project.logoSrc}
                alt={project.name}
              />
              <span className="finos-project-copy">{project.description}</span>
            </a>
          </li>
        ))}
      </ul>
    </aside>
  )
}
