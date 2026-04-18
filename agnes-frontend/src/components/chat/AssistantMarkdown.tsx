import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { cn } from '@/lib/utils';

interface AssistantMarkdownProps {
  content: string;
  className?: string;
}

export function AssistantMarkdown({ content, className }: AssistantMarkdownProps) {
  return (
    <div className={cn('prose-agnes', className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          a: (props: any) => (
            <a
              href={props.href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-violet-300 hover:text-violet-200 underline underline-offset-2 decoration-violet-500/40 hover:decoration-violet-300 transition-colors"
            >
              {props.children}
            </a>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          h1: (props: any) => (
            <h1 className="text-xl font-semibold mt-4 mb-2 text-white">{props.children}</h1>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          h2: (props: any) => (
            <h2 className="text-lg font-semibold mt-4 mb-2 text-white/95">{props.children}</h2>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          h3: (props: any) => (
            <h3 className="text-base font-semibold mt-3 mb-1.5 text-white/90">{props.children}</h3>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          h4: (props: any) => (
            <h4 className="text-sm font-semibold mt-2 mb-1 text-white/85">{props.children}</h4>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          code: (props: any) => {
            const isBlock = props.className && props.className.startsWith('language-');
            return isBlock ? (
              <code className="block bg-black/40 p-3 rounded-md text-xs overflow-x-auto font-mono text-white/80">
                {props.children}
              </code>
            ) : (
              <code className="bg-white/10 px-1.5 py-0.5 rounded text-xs text-violet-200 font-mono">
                {props.children}
              </code>
            );
          },
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          pre: (props: any) => (
            <pre className="my-2 overflow-x-auto">{props.children}</pre>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          ul: (props: any) => (
            <ul className="list-disc pl-5 space-y-1 my-2 text-white/80">{props.children}</ul>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          ol: (props: any) => (
            <ol className="list-decimal pl-5 space-y-1 my-2 text-white/80">{props.children}</ol>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          li: (props: any) => (
            <li className="text-white/80 leading-relaxed">{props.children}</li>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          p: (props: any) => (
            <p className="my-2 text-white/80 leading-relaxed">{props.children}</p>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          strong: (props: any) => (
            <strong className="font-semibold text-white">{props.children}</strong>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          em: (props: any) => (
            <em className="text-white/70 italic">{props.children}</em>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          blockquote: (props: any) => (
            <blockquote className="border-l-2 border-violet-500/40 pl-4 my-3 text-white/70 italic">
              {props.children}
            </blockquote>
          ),
          hr: () => <hr className="border-white/10 my-4" />,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          table: (props: any) => (
            <div className="overflow-x-auto my-3">
              <table className="min-w-full text-sm border-collapse">{props.children}</table>
            </div>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          thead: (props: any) => (
            <thead className="border-b border-white/20">{props.children}</thead>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          th: (props: any) => (
            <th className="text-left px-3 py-1.5 text-white/90 font-medium text-xs uppercase tracking-wider">
              {props.children}
            </th>
          ),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          td: (props: any) => (
            <td className="px-3 py-1.5 text-white/70 border-b border-white/5">{props.children}</td>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
