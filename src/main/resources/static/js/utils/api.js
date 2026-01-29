/**
 * API utility module for Bible-AI frontend.
 */

const API_BASE = '/api/bible';

/**
 * Send a chat query to the agent.
 * @param {string} query - The user's query
 * @param {string} sessionId - Session ID for conversation context
 * @returns {Promise<Object>} - The agent's response
 */
export async function sendQuery(query, sessionId) {
    const response = await fetch(`${API_BASE}/query`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query, sessionId })
    });
    
    if (!response.ok) {
        throw new Error(`Query failed: ${response.statusText}`);
    }
    
    return response.json();
}

/**
 * Get a specific verse.
 * @param {string} book - Book name or short code
 * @param {number} chapter - Chapter number
 * @param {number} verse - Verse number
 * @returns {Promise<Object>} - Verse data
 */
export async function getVerse(book, chapter, verse) {
    const response = await fetch(
        `${API_BASE}/${encodeURIComponent(book)}/${chapter}/${verse}`
    );
    
    if (!response.ok) {
        throw new Error(`Verse not found: ${book} ${chapter}:${verse}`);
    }
    
    return response.json();
}

/**
 * Get verse with context (surrounding verses).
 * @param {string} book - Book name or short code
 * @param {number} chapter - Chapter number
 * @param {number} verse - Verse number
 * @param {number} contextSize - Number of verses before/after (default 2)
 * @returns {Promise<Object>} - Verse with context data
 */
export async function getVerseWithContext(book, chapter, verse, contextSize = 2) {
    const response = await fetch(
        `${API_BASE}/${encodeURIComponent(book)}/${chapter}/${verse}/context?size=${contextSize}`
    );
    
    if (!response.ok) {
        throw new Error(`Failed to get verse context`);
    }
    
    return response.json();
}

/**
 * Get a full chapter.
 * @param {string} book - Book name or short code
 * @param {number} chapter - Chapter number
 * @param {string} version - Bible version (optional)
 * @returns {Promise<Object>} - Chapter data
 */
export async function getChapter(book, chapter, version = null) {
    const params = version ? `?version=${version}` : '';
    const response = await fetch(
        `${API_BASE}/${encodeURIComponent(book)}/${chapter}${params}`
    );
    
    if (!response.ok) {
        throw new Error(`Chapter not found: ${book} ${chapter}`);
    }
    
    return response.json();
}

/**
 * Perform smart search.
 * @param {string} query - Search query
 * @param {number} maxResults - Maximum results (optional)
 * @param {number} minScore - Minimum relevance score (optional)
 * @param {string} version - Bible version filter (optional)
 * @returns {Promise<Object>} - Search results
 */
export async function search(query, maxResults = null, minScore = null, version = null) {
    const response = await fetch(`${API_BASE}/search`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query, maxResults, minScore, version })
    });
    
    if (!response.ok) {
        throw new Error(`Search failed: ${response.statusText}`);
    }
    
    return response.json();
}

/**
 * Get app configuration.
 * @returns {Promise<Object>} - Config data
 */
export async function getConfig() {
    const response = await fetch(`${API_BASE}/config`);
    return response.json();
}

/**
 * Get agent and search statistics.
 * @returns {Promise<Object>} - Statistics data
 */
export async function getStats() {
    const response = await fetch(`${API_BASE}/stats`);
    return response.json();
}
