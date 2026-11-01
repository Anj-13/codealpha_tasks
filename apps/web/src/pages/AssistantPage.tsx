import { useEffect, useRef, useState, type FormEvent } from 'react';
import { sendChatMessage } from '../api/chat';
import { ApiError, ServiceOfflineError } from '../api/client';
import { Badge, PageHeader, ServiceBanner } from '../components/ui';

const SUGGESTIONS = [
  'show routes',
  'what are the fares',
  'how does validation work',
  'why was my record flagged',
  'check my booking',
  'show redundancy records',
  'help',
];

const MAX_MESSAGE_LENGTH = 500;

interface Message {
  id: number;
  role: 'user' | 'assistant' | 'error';
  text: string;
  source?: 'STATIC' | 'LIVE';
}

const WELCOME: Message = {
  id: 0,
  role: 'assistant',
  text: 'Hello! Ask about routes, fares, bookings or data validation, or pick a suggestion.',
};

function failureText(error: unknown): string {
  if (error instanceof ServiceOfflineError) {
    return 'The assistant is offline. Start it to continue this conversation.';
  }
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.';
}

export function AssistantPage() {
  const [messages, setMessages] = useState<Message[]>([WELCOME]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const nextId = useRef(1);
  const log = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = log.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages, busy]);

  function append(message: Omit<Message, 'id'>) {
    const id = nextId.current++;
    setMessages((current) => [...current, { ...message, id }]);
  }

  async function ask(question: string) {
    const trimmed = question.trim();
    if (!trimmed || busy) {
      return;
    }
    append({ role: 'user', text: trimmed });
    setText('');
    setBusy(true);
    try {
      const reply = await sendChatMessage(trimmed);
      append({ role: 'assistant', text: reply.response, source: reply.source });
    } catch (error) {
      append({ role: 'error', text: failureText(error) });
    } finally {
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void ask(text);
  }

  return (
    <>
      <PageHeader title="Assistant">
        A retrieval-based assistant. It matches your message against predefined rules, then answers
        from fixed responses or live lookups. It is not a generative AI model.
      </PageHeader>
      <ServiceBanner service="chatbot" />

      <section className="card chat" aria-label="Conversation">
        <div ref={log} className="chat-log" role="log" aria-live="polite" tabIndex={0} aria-label="Messages">
          {messages.map((message) => (
            <div key={message.id} className={`chat-message chat-${message.role}`}>
              <p>{message.text}</p>
              {message.source === 'LIVE' && <Badge tone="success">Live data</Badge>}
            </div>
          ))}
          {busy && <p className="chat-typing">Looking that up…</p>}
        </div>

        <div className="chat-suggestions" aria-label="Suggested questions" role="group">
          {SUGGESTIONS.map((suggestion) => (
            <button key={suggestion} type="button" className="chip" disabled={busy} onClick={() => void ask(suggestion)}>
              {suggestion}
            </button>
          ))}
        </div>

        <form className="chat-form" onSubmit={submit}>
          <label htmlFor="chat-input" className="sr-only">Your message</label>
          <input
            id="chat-input"
            value={text}
            maxLength={MAX_MESSAGE_LENGTH}
            autoComplete="off"
            placeholder="Type a message"
            onChange={(event) => setText(event.target.value)}
          />
          <button type="submit" className="button button-primary" disabled={busy || !text.trim()}>Send</button>
        </form>
      </section>
    </>
  );
}
