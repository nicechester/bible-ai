/**
 * ChatPane component for conversation display and input.
 * Note: This is designed to be used with React loaded from CDN.
 */

/**
 * Create the ChatPane React component.
 * @param {Object} React - React library
 * @returns {Function} - React component
 */
export function createChatPane(React) {
    const { useState, useEffect, useRef } = React;
    
    return function ChatPane({ 
        messages, 
        loading, 
        onSendMessage, 
        onNewSession,
        showGreeting 
    }) {
        const [input, setInput] = useState('');
        const messagesEndRef = useRef(null);
        const inputRef = useRef(null);
        
        // Auto-scroll to bottom
        const scrollToBottom = () => {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        };
        
        useEffect(() => {
            scrollToBottom();
        }, [messages, showGreeting]);
        
        // Send message handler
        const handleSend = () => {
            if (!input.trim() || loading) return;
            onSendMessage(input.trim());
            setInput('');
        };
        
        // Key press handler
        const handleKeyPress = (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
            }
        };
        
        // Focus input on new session
        useEffect(() => {
            if (inputRef.current) {
                inputRef.current.focus();
            }
        }, []);
        
        return React.createElement('div', { className: 'conversation-pane', style: { width: '30%' } },
            // Header
            React.createElement('div', { className: 'conversation-header' },
                React.createElement('span', null, 'Conversation'),
                React.createElement('button', {
                    className: 'new-session-button',
                    onClick: onNewSession,
                    title: 'Start new session'
                }, '+')
            ),
            
            // Messages container
            React.createElement('div', { className: 'messages-container' },
                // Greeting
                showGreeting && React.createElement('div', { className: 'greeting-message' },
                    React.createElement('h3', null, '📖 Welcome to Bible AI'),
                    React.createElement('p', null, 
                        "I'm your Bible study assistant. I can help you search and understand the Korean Revised Version (개역개정) Bible."
                    ),
                    React.createElement('p', { style: { marginTop: '8px' } }, 'Try asking questions like:'),
                    React.createElement('ul', null,
                        React.createElement('li', null, '"창세기 1장 1절을 보여줘"'),
                        React.createElement('li', null, '"사랑에 대한 구절을 찾아줘"'),
                        React.createElement('li', null, '"요한복음 3장을 읽어줘"'),
                        React.createElement('li', null, '"예수님의 비유를 설명해줘"')
                    )
                ),
                
                // Messages
                messages.map((msg, idx) => 
                    React.createElement('div', {
                        key: idx,
                        className: `message ${msg.role}`
                    }, msg.content)
                ),
                
                // Loading indicator
                loading && React.createElement('div', { className: 'loading' }, 'Thinking...'),
                
                // Scroll anchor
                React.createElement('div', { ref: messagesEndRef })
            ),
            
            // Input container
            React.createElement('div', { className: 'input-container' },
                React.createElement('div', { className: 'input-form' },
                    React.createElement('input', {
                        ref: inputRef,
                        className: 'input-field',
                        type: 'text',
                        value: input,
                        onChange: (e) => setInput(e.target.value),
                        onKeyPress: handleKeyPress,
                        placeholder: 'Ask a question about the Bible...',
                        disabled: loading
                    }),
                    React.createElement('button', {
                        className: 'send-button',
                        onClick: handleSend,
                        disabled: loading || !input.trim()
                    }, 'Send')
                )
            )
        );
    };
}

export default createChatPane;
